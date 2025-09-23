package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.util.*;
import java.util.logging.Level;
import java.util.regex.Pattern;

import static io.github.simbo1905.json.schema.JsonSchema.LOG;

/// Internal schema compiler
public final class SchemaCompiler {
  public static boolean formsRemoteCycle(Map<URI, URI> parentMap,
                                         URI currentDocUri,
                                         URI targetDocUri) {
    if (currentDocUri.equals(targetDocUri)) {
      return true;
    }

    URI cursor = currentDocUri;
    while (true) {
      URI parent = parentMap.get(cursor);
      if (parent == null) {
        break;
      }
      if (parent.equals(targetDocUri)) {
        return true;
      }
      cursor = parent;
    }
    return false;
  }

  /// Per-compilation session state (no static mutable fields).
  private static final class Session {
    final Map<String, JsonValue> rawByPointer = new LinkedHashMap<>();
    final Map<java.net.URI, java.net.URI> parentMap = new LinkedHashMap<>();
    JsonSchema currentRootSchema;
    JsonSchema.JsonSchemaOptions currentJsonSchemaOptions;
    long totalFetchedBytes;
    int fetchedDocs;
  }

  /// Strip any fragment from a URI, returning the base document URI.
  private static java.net.URI stripFragment(java.net.URI uri) {
    String s = uri.toString();
    int i = s.indexOf('#');
    java.net.URI base = i >= 0 ? java.net.URI.create(s.substring(0, i)) : uri;
    return base.normalize();
  }
  // removed static mutable state; state now lives in Session

  private static void trace(String stage, JsonValue fragment) {
    if (LOG.isLoggable(Level.FINER)) {
      LOG.finer(() ->
          String.format("[%s] %s", stage, fragment.toString()));
    }
  }

  /// Per-compile carrier for resolver-related state.
  private static final class CompileContext {
    final Session session;
    final Map<java.net.URI, JsonSchema.CompiledRoot> sharedRoots;
    final JsonSchema.ResolverContext resolverContext;
    final Map<String, JsonSchema> localPointerIndex;
    final Deque<String> resolutionStack;
    final Deque<ContextFrame> frames = new ArrayDeque<>();

    CompileContext(Session session,
                   Map<java.net.URI, JsonSchema.CompiledRoot> sharedRoots,
                   JsonSchema.ResolverContext resolverContext,
                   Map<String, JsonSchema> localPointerIndex,
                   Deque<String> resolutionStack) {
      this.session = session;
      this.sharedRoots = sharedRoots;
      this.resolverContext = resolverContext;
      this.localPointerIndex = localPointerIndex;
      this.resolutionStack = resolutionStack;
    }
  }

  /// Immutable context frame capturing current document/base/pointer/anchors.
  private record ContextFrame(URI docUri, URI baseUri, String pointer, Map<String, String> anchors) {
    private ContextFrame(URI docUri, URI baseUri, String pointer, Map<String, String> anchors) {
      this.docUri = docUri;
      this.baseUri = baseUri;
      this.pointer = pointer;
      this.anchors = anchors == null ? Map.of() : Map.copyOf(anchors);
    }
  }

  /// JSON Pointer utility for RFC-6901 fragment navigation
  static Optional<JsonValue> navigatePointer(JsonValue root, String pointer) {
    LOG.fine(() -> "pointer.navigate pointer=" + pointer);


    if (pointer.isEmpty() || pointer.equals(JsonSchema.SCHEMA_POINTER_ROOT)) {
      return Optional.of(root);
    }

    // Remove leading # if present
    String path = pointer.startsWith(JsonSchema.SCHEMA_POINTER_ROOT) ? pointer.substring(1) : pointer;
    if (path.isEmpty()) {
      return Optional.of(root);
    }

    // Must start with /
    if (!path.startsWith("/")) {
      return Optional.empty();
    }

    JsonValue current = root;
    String[] tokens = path.substring(1).split("/");

    // Performance warning for deeply nested pointers
    if (tokens.length > 50) {
      final int tokenCount = tokens.length;
      LOG.warning(() -> "PERFORMANCE WARNING: Navigating deeply nested JSON pointer with " + tokenCount +
          " segments - possible performance impact");
    }

    for (int i = 0; i < tokens.length; i++) {
      if (i > 0 && i % 25 == 0) {
        final int segment = i;
        final int total = tokens.length;
        LOG.warning(() -> "PERFORMANCE WARNING: JSON pointer navigation at segment " + segment + " of " + total);
      }

      String token = tokens[i];
      // Unescape ~1 -> / and ~0 -> ~
      String unescaped = token.replace("~1", "/").replace("~0", "~");
      final var currentFinal = current;
      final var unescapedFinal = unescaped;

      LOG.finer(() -> "Token: '" + token + "' unescaped: '" + unescapedFinal + "' current: " + currentFinal);

      if (current instanceof JsonObject obj) {
        current = obj.members().get(unescaped);
        if (current == null) {
          LOG.finer(() -> "Property not found: " + unescapedFinal);
          return Optional.empty();
        }
      } else if (current instanceof JsonArray arr) {
        try {
          int index = Integer.parseInt(unescaped);
          if (index < 0 || index >= arr.values().size()) {
            return Optional.empty();
          }
          current = arr.values().get(index);
        } catch (NumberFormatException e) {
          return Optional.empty();
        }
      } else {
        return Optional.empty();
      }
    }

    LOG.fine(() -> "pointer.navigate pointer=" + pointer);

    return Optional.of(current);
  }

  /// Classify a $ref string as local or remote
  static JsonSchema.RefToken classifyRef(String ref, URI baseUri) {
    LOG.fine(() -> "ref.classify ref=" + ref + " base=" + baseUri);


    if (ref == null || ref.isEmpty()) {
      throw new IllegalArgumentException("InvalidPointer: empty $ref");
    }

    // Check if it's a URI with scheme (remote) or just fragment/local pointer
    try {
      URI refUri = URI.create(ref);

      // If it has a scheme or authority, it's remote
      if (refUri.getScheme() != null || refUri.getAuthority() != null) {
        URI resolvedUri = baseUri.resolve(refUri);
        LOG.finer(() -> "ref.classified kind=remote uri=" + resolvedUri);

        return new JsonSchema.RefToken.RemoteRef(baseUri, resolvedUri);
      }

      // If it's just a fragment or starts with #, it's local
      if (ref.startsWith(JsonSchema.SCHEMA_POINTER_ROOT) || !ref.contains("://")) {
        LOG.finer(() -> "ref is local root " + ref);
        return new JsonSchema.RefToken.LocalRef(ref);
      }

      // Default to local for safety during this refactor
      LOG.finer(() -> "ref is not local root " + ref);
      throw new AssertionError("not implemented");
      //return new RefToken.LocalRef(ref);
    } catch (IllegalArgumentException e) {
      // Invalid URI syntax - treat as local pointer with error handling
      if (ref.startsWith(JsonSchema.SCHEMA_POINTER_ROOT) || ref.startsWith("/")) {
        LOG.finer(() -> "Invalid URI but treating as local ref: " + ref);
        return new JsonSchema.RefToken.LocalRef(ref);
      }
      throw new IllegalArgumentException("InvalidPointer: " + ref);
    }
  }

  /// Index schema fragments by JSON Pointer for efficient lookup
  static void indexSchemaByPointer(Session session, String pointer, JsonValue value) {
    session.rawByPointer.put(pointer, value);

    if (value instanceof JsonObject obj) {
      for (var entry : obj.members().entrySet()) {
        String key = entry.getKey();
        // Escape special characters in key
        String escapedKey = key.replace("~", "~0").replace("/", "~1");
        indexSchemaByPointer(session, pointer + "/" + escapedKey, entry.getValue());
      }
    } else if (value instanceof JsonArray arr) {
      for (int i = 0; i < arr.values().size(); i++) {
        indexSchemaByPointer(session, pointer + "/" + i, arr.values().get(i));
      }
    }
  }

  /// New stack-driven compilation method that creates CompilationBundle
  static JsonSchema.CompilationBundle compileBundle(JsonValue schemaJson, JsonSchema.JsonSchemaOptions jsonSchemaOptions, JsonSchema.CompileOptions compileOptions) {
    LOG.fine(() -> "compileBundle: Starting with remote compilation enabled");

    Session session = new Session();

    // Work stack for documents to compile
    Deque<JsonSchema.WorkItem> workStack = new ArrayDeque<>();
    Set<URI> seenUris = new HashSet<>();
    Map<URI, JsonSchema.CompiledRoot> compiled = new JsonSchema.NormalizedUriMap(new LinkedHashMap<>());

    // Start with synthetic URI for in-memory root
    URI entryUri = URI.create("urn:inmemory:root");
    LOG.finest(() -> "compileBundle: Entry URI: " + entryUri);
    workStack.push(new JsonSchema.WorkItem(entryUri));
    seenUris.add(entryUri);

    LOG.fine(() -> "compileBundle: Initialized work stack with entry URI: " + entryUri + ", workStack size: " + workStack.size());

    // Process work stack
    int processedCount = 0;
    final int WORK_WARNING_THRESHOLD = 16; // Warn after processing 16 documents

    while (!workStack.isEmpty()) {
      processedCount++;
      final int finalProcessedCount = processedCount;
      if (processedCount % WORK_WARNING_THRESHOLD == 0) {
        LOG.warning(() -> "PERFORMANCE WARNING: compileBundle processing document " + finalProcessedCount +
            " - large document chains may impact performance");
      }

      JsonSchema.WorkItem workItem = workStack.pop();
      URI currentUri = workItem.docUri();
      final int currentProcessedCount = processedCount;
      LOG.finer(() -> "compileBundle: Processing URI: " + currentUri + " (processed count: " + currentProcessedCount + ")");

      // Skip if already compiled
      if (compiled.containsKey(currentUri)) {
        LOG.finer(() -> "compileBundle: Already compiled, skipping: " + currentUri);
        continue;
      }

      // Handle remote URIs
      JsonValue documentToCompile;
      if (currentUri.equals(entryUri)) {
        // Entry document - use provided schema
        documentToCompile = schemaJson;
        LOG.finer(() -> "compileBundle: Using entry document for URI: " + currentUri);
      } else {
        // Remote document - fetch it
        LOG.finer(() -> "compileBundle: Fetching remote URI: " + currentUri);

        // Remove fragment from URI to get document URI
        String fragment = currentUri.getFragment();
        URI docUri = fragment != null ?
            URI.create(currentUri.toString().substring(0, currentUri.toString().indexOf('#'))) :
            currentUri;

        LOG.finest(() -> "compileBundle: Document URI after fragment removal: " + docUri);

        // Enforce allowed schemes before invoking fetcher
        String scheme = docUri.getScheme();
        LOG.fine(() -> "compileBundle: evaluating fetch for docUri=" + docUri + ", scheme=" + scheme + ", allowedSchemes=" + compileOptions.fetchPolicy().allowedSchemes());
        if (scheme == null || !compileOptions.fetchPolicy().allowedSchemes().contains(scheme)) {
          throw new RemoteResolutionException(
              docUri,
              RemoteResolutionException.Reason.POLICY_DENIED,
              "Scheme not allowed by policy: " + scheme
          );
        }

        // Enforce global document count before fetching
        if (session.fetchedDocs + 1 > compileOptions.fetchPolicy().maxDocuments()) {
          throw new RemoteResolutionException(
              docUri,
              RemoteResolutionException.Reason.POLICY_DENIED,
              "Maximum document count exceeded for " + docUri
          );
        }

        JsonSchema.RemoteFetcher.FetchResult fetchResult =
            compileOptions.remoteFetcher().fetch(docUri, compileOptions.fetchPolicy());

        if (fetchResult.byteSize() > compileOptions.fetchPolicy().maxDocumentBytes()) {
          throw new RemoteResolutionException(
              docUri,
              RemoteResolutionException.Reason.PAYLOAD_TOO_LARGE,
              "Remote document exceeds max allowed bytes at " + docUri + ": " + fetchResult.byteSize()
          );
        }
        if (fetchResult.elapsed().isPresent() && fetchResult.elapsed().get().compareTo(compileOptions.fetchPolicy().timeout()) > 0) {
          throw new RemoteResolutionException(
              docUri,
              RemoteResolutionException.Reason.TIMEOUT,
              "Remote fetch exceeded timeout at " + docUri + ": " + fetchResult.elapsed().get()
          );
        }

        // Update global counters and enforce total bytes across the compilation
        session.fetchedDocs++;
        session.totalFetchedBytes += fetchResult.byteSize();
        if (session.totalFetchedBytes > compileOptions.fetchPolicy().maxTotalBytes()) {
          throw new RemoteResolutionException(
              docUri,
              RemoteResolutionException.Reason.POLICY_DENIED,
              "Total fetched bytes exceeded policy across documents at " + docUri + ": " + session.totalFetchedBytes
          );
        }

        documentToCompile = fetchResult.document();
        final String normType = documentToCompile.getClass().getSimpleName();
        final URI normUri = docUri;
        LOG.fine(() -> "compileBundle: Successfully fetched document: " + normUri + ", document type: " + normType);
      }

      // Compile the schema
      JsonSchema.CompilationResult result = compileSingleDocument(session, documentToCompile, jsonSchemaOptions, currentUri, workStack, seenUris, compiled);

      // Create compiled root and add to map
      JsonSchema.CompiledRoot compiledRoot = new JsonSchema.CompiledRoot(currentUri, result.schema(), result.pointerIndex());
      compiled.put(currentUri, compiledRoot);
      LOG.fine(() -> "compileBundle: Added compiled root for URI: " + currentUri +
          " with " + result.pointerIndex().size() + " pointer index entries");
    }

    // Create compilation bundle
    JsonSchema.CompiledRoot entryRoot = compiled.get(entryUri);
    if (entryRoot == null) {
      LOG.severe(() -> "ERROR: SCHEMA: entry root null doc=" + entryUri);
    }
    assert entryRoot != null : "Entry root must exist";
    List<JsonSchema.CompiledRoot> allRoots = List.copyOf(compiled.values());

    LOG.fine(() -> "compileBundle: Creating compilation bundle with " + allRoots.size() + " total compiled roots");

    // Create a map of compiled roots for resolver context
    Map<URI, JsonSchema.CompiledRoot> rootsMap = new LinkedHashMap<>();
    LOG.finest(() -> "compileBundle: Creating rootsMap from " + allRoots.size() + " compiled roots");
    for (JsonSchema.CompiledRoot root : allRoots) {
      LOG.finest(() -> "compileBundle: Adding root to map: " + root.docUri());
      // Add both with and without fragment for lookup flexibility
      rootsMap.put(root.docUri(), root);
      // Also add the base URI without fragment if it has one
      if (root.docUri().getFragment() != null) {
        URI baseUri = URI.create(root.docUri().toString().substring(0, root.docUri().toString().indexOf('#')));
        rootsMap.put(baseUri, root);
        LOG.finest(() -> "compileBundle: Also adding base URI: " + baseUri);
      }
    }
    LOG.finest(() -> "compileBundle: Final rootsMap keys: " + rootsMap.keySet());

    // Create compilation bundle with compiled roots
    List<JsonSchema.CompiledRoot> updatedRoots = List.copyOf(compiled.values());
    JsonSchema.CompiledRoot updatedEntryRoot = compiled.get(entryUri);

    LOG.fine(() -> "compileBundle: Successfully created compilation bundle with " + updatedRoots.size() +
        " total documents compiled, entry root type: " + updatedEntryRoot.schema().getClass().getSimpleName());
    LOG.finest(() -> "compileBundle: Completed with entry root: " + updatedEntryRoot);
    return new JsonSchema.CompilationBundle(updatedEntryRoot, updatedRoots);
  }

  /// Compile a single document using new architecture
  static JsonSchema.CompilationResult compileSingleDocument(Session session, JsonValue schemaJson, JsonSchema.JsonSchemaOptions jsonSchemaOptions,
                                                            URI docUri, Deque<JsonSchema.WorkItem> workStack, Set<URI> seenUris,
                                                            Map<URI, JsonSchema.CompiledRoot> sharedRoots) {
    LOG.fine(() -> "compileSingleDocument: Starting compilation for docUri: " + docUri + ", schema type: " + schemaJson.getClass().getSimpleName());

    // Initialize session state
    session.rawByPointer.clear();
    session.currentRootSchema = null;
    session.currentJsonSchemaOptions = jsonSchemaOptions;

    LOG.finest(() -> "compileSingleDocument: Reset global state, definitions cleared, pointer indexes cleared");

    // Handle format assertion controls
    boolean assertFormats = jsonSchemaOptions.assertFormats();

    // Check system property first (read once during compile)
    String systemProp = System.getProperty("jsonschema.format.assertion");
    if (systemProp != null) {
      assertFormats = Boolean.parseBoolean(systemProp);
      final boolean finalAssertFormats = assertFormats;
      LOG.finest(() -> "compileSingleDocument: Format assertion overridden by system property: " + finalAssertFormats);
    }

    // Check root schema flag (highest precedence)
    if (schemaJson instanceof JsonObject obj) {
      JsonValue formatAssertionValue = obj.members().get("formatAssertion");
      if (formatAssertionValue instanceof JsonBoolean formatAssertionBool) {
        assertFormats = formatAssertionBool.value();
        final boolean finalAssertFormats = assertFormats;
        LOG.finest(() -> "compileSingleDocument: Format assertion overridden by root schema flag: " + finalAssertFormats);
      }
    }

    // Update jsonSchemaOptions with final assertion setting
    session.currentJsonSchemaOptions = new JsonSchema.JsonSchemaOptions(assertFormats);
    final boolean finalAssertFormats = assertFormats;
    LOG.finest(() -> "compileSingleDocument: Final format assertion setting: " + finalAssertFormats);

    // Index the raw schema by JSON Pointer
    LOG.finest(() -> "compileSingleDocument: Indexing schema by pointer");
    indexSchemaByPointer(session, "", schemaJson);

    // Build local pointer index for this document
    Map<String, JsonSchema> localPointerIndex = new LinkedHashMap<>();

    trace("compile-start", schemaJson);
    LOG.finer(() -> "compileSingleDocument: Calling compileInternalWithContext for docUri: " + docUri);
    CompileContext ctx = new CompileContext(
        session,
        sharedRoots,
        new JsonSchema.ResolverContext(sharedRoots, localPointerIndex, AnySchema.INSTANCE),
        localPointerIndex,
        new ArrayDeque<>()
    );
    // Initialize frame stack with entry doc and root pointer
    ctx.frames.push(new ContextFrame(docUri, docUri, JsonSchema.SCHEMA_POINTER_ROOT, Map.of()));
    JsonSchema schema = compileWithContext(ctx, schemaJson, docUri, workStack, seenUris);
    LOG.finer(() -> "compileSingleDocument: compileInternalWithContext completed, schema type: " + schema.getClass().getSimpleName());

    session.currentRootSchema = schema; // Store the root schema for self-references
    LOG.fine(() -> "compileSingleDocument: Completed compilation for docUri: " + docUri +
        ", schema type: " + schema.getClass().getSimpleName() + ", local pointer index size: " + localPointerIndex.size());
    return new JsonSchema.CompilationResult(schema, Map.copyOf(localPointerIndex));
  }

  private static JsonSchema compileWithContext(CompileContext ctx,
                                               JsonValue schemaJson,
                                               URI docUri,
                                               Deque<JsonSchema.WorkItem> workStack,
                                               Set<URI> seenUris) {
    String basePointer = ctx.frames.isEmpty() ? JsonSchema.SCHEMA_POINTER_ROOT : ctx.frames.peek().pointer;
    return compileInternalWithContext(
        ctx.session,
        schemaJson,
        docUri,
        workStack,
        seenUris,
        ctx.resolverContext,
        ctx.localPointerIndex,
        ctx.resolutionStack,
        ctx.sharedRoots,
        basePointer
    );
  }

  private static JsonSchema compileInternalWithContext(Session session, JsonValue schemaJson, URI docUri,
                                                       Deque<JsonSchema.WorkItem> workStack, Set<URI> seenUris,
                                                       JsonSchema.ResolverContext resolverContext,
                                                       Map<String, JsonSchema> localPointerIndex,
                                                       Deque<String> resolutionStack,
                                                       Map<URI, JsonSchema.CompiledRoot> sharedRoots,
                                                       String basePointer) {
    LOG.fine(() -> "compileInternalWithContext: Starting with schema: " + schemaJson + ", docUri: " + docUri);

    // Check for $ref at this level first
    if (schemaJson instanceof JsonObject obj) {
      JsonValue refValue = obj.members().get("$ref");
      if (refValue instanceof JsonString refStr) {
        LOG.fine(() -> "compileInternalWithContext: Found $ref: " + refStr.value());
        JsonSchema.RefToken refToken = classifyRef(refStr.value(), docUri);

        // Handle remote refs by adding to work stack
        RefSchema refSchema = new RefSchema(refToken, new JsonSchema.ResolverContext(sharedRoots, localPointerIndex, AnySchema.INSTANCE));
        if (refToken instanceof JsonSchema.RefToken.RemoteRef remoteRef) {
          LOG.finer(() -> "Remote ref detected: " + remoteRef.targetUri());
          URI targetDocUri = stripFragment(remoteRef.targetUri());
          LOG.fine(() -> "Remote ref scheduling from docUri=" + docUri + " to target=" + targetDocUri);
          LOG.finest(() -> "Remote ref parentMap before cycle check: " + session.parentMap);
          if (formsRemoteCycle(session.parentMap, docUri, targetDocUri)) {
            String cycleMessage = "ERROR: CYCLE: remote $ref cycle detected current=" + docUri + ", target=" + targetDocUri;
            LOG.severe(() -> cycleMessage);
            throw new IllegalStateException(cycleMessage);
          }
          boolean alreadySeen = seenUris.contains(targetDocUri);
          LOG.finest(() -> "Remote ref alreadySeen=" + alreadySeen + " for target=" + targetDocUri);
          if (!alreadySeen) {
            workStack.push(new JsonSchema.WorkItem(targetDocUri));
            seenUris.add(targetDocUri);
            session.parentMap.putIfAbsent(targetDocUri, docUri);
            LOG.finer(() -> "Added to work stack: " + targetDocUri);
          } else {
            session.parentMap.putIfAbsent(targetDocUri, docUri);
            LOG.finer(() -> "Remote ref already scheduled or compiled: " + targetDocUri);
          }
          LOG.finest(() -> "Remote ref parentMap after scheduling: " + session.parentMap);
          LOG.finest(() -> "compileInternalWithContext: Creating RefSchema for remote ref " + remoteRef.targetUri());

          LOG.fine(() -> "Creating RefSchema for remote ref " + remoteRef.targetUri() +
              " with localPointerEntries=" + localPointerIndex.size());

          LOG.finest(() -> "compileInternalWithContext: Created RefSchema " + refSchema);
          return refSchema;
        }

        // Handle local refs - check if they exist first and detect cycles
        LOG.finer(() -> "Local ref detected, creating RefSchema: " + refToken.pointer());

        String pointer = refToken.pointer();

        // For compilation-time validation, check if the reference exists
        if (!pointer.equals(JsonSchema.SCHEMA_POINTER_ROOT) && !pointer.isEmpty() && !localPointerIndex.containsKey(pointer)) {
          // Check if it might be resolvable via JSON Pointer navigation
          Optional<JsonValue> target = navigatePointer(session.rawByPointer.get(""), pointer);
          if (target.isEmpty() && basePointer != null && !basePointer.isEmpty() && pointer.startsWith(JsonSchema.SCHEMA_POINTER_PREFIX)) {
            String combined = basePointer + pointer.substring(1);
            target = navigatePointer(session.rawByPointer.get(""), combined);
          }
          if (target.isEmpty() && !pointer.startsWith(JsonSchema.SCHEMA_DEFS_POINTER)) {
            throw new IllegalArgumentException("Unresolved $ref: " + pointer);
          }
        }

        // Check for cycles and resolve immediately for $defs references
        if (pointer.startsWith(JsonSchema.SCHEMA_DEFS_POINTER)) {
          // This is a definition reference - check for cycles and resolve immediately
          if (resolutionStack.contains(pointer)) {
            throw new IllegalArgumentException("CYCLE: Cyclic $ref: " + String.join(" -> ", resolutionStack) + " -> " + pointer);
          }

          // Try to get from local pointer index first (for already compiled definitions)
          JsonSchema cached = localPointerIndex.get(pointer);
          if (cached != null) {
            return cached;
          }

          // Otherwise, resolve via JSON Pointer and compile
          Optional<JsonValue> target = navigatePointer(session.rawByPointer.get(""), pointer);
          if (target.isEmpty() && pointer.startsWith(JsonSchema.SCHEMA_DEFS_POINTER)) {
            // Heuristic fallback: locate the same named definition under any nested $defs
            String defName = pointer.substring(JsonSchema.SCHEMA_DEFS_POINTER.length());
            // Perform a shallow search over indexed pointers for a matching suffix
            for (var entry2 : session.rawByPointer.entrySet()) {
              String k = entry2.getKey();
              if (k.endsWith(JsonSchema.SCHEMA_DEFS_SEGMENT + defName)) {
                target = Optional.ofNullable(entry2.getValue());
                break;
              }
            }
          }
          if (target.isEmpty() && basePointer != null && !basePointer.isEmpty() && pointer.startsWith(JsonSchema.SCHEMA_POINTER_PREFIX)) {
            String combined = basePointer + pointer.substring(1);
            target = navigatePointer(session.rawByPointer.get(""), combined);
          }
          if (target.isPresent()) {
            // Check if the target itself contains a $ref that would create a cycle
            JsonValue targetValue = target.get();
            if (targetValue instanceof JsonObject targetObj) {
              JsonValue targetRef = targetObj.members().get("$ref");
              if (targetRef instanceof JsonString targetRefStr) {
                String targetRefPointer = targetRefStr.value();
                if (resolutionStack.contains(targetRefPointer)) {
                  throw new IllegalArgumentException("CYCLE: Cyclic $ref: " + String.join(" -> ", resolutionStack) + " -> " + pointer + " -> " + targetRefPointer);
                }
              }
            }

            // Push to resolution stack for cycle detection before compiling
            resolutionStack.push(pointer);
            try {
              JsonSchema compiled = compileInternalWithContext(session, targetValue, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, sharedRoots, basePointer);
              localPointerIndex.put(pointer, compiled);
              return compiled;
            } finally {
              resolutionStack.pop();
            }
          } else {
            throw new IllegalArgumentException("Unresolved $ref: " + pointer);
          }
        }

        // Handle root reference (#) specially - use RootRef instead of RefSchema
        if (pointer.equals(JsonSchema.SCHEMA_POINTER_ROOT) || pointer.isEmpty()) {
          // For root reference, create RootRef that will resolve through ResolverContext
          // The ResolverContext will be updated later with the proper root schema
          return new RootRef(() -> {
            // Prefer the session root once available, otherwise use resolver context placeholder.
            if (session.currentRootSchema != null) {
              return session.currentRootSchema;
            }
            if (resolverContext != null) {
              return resolverContext.rootSchema();
            }
            return AnySchema.INSTANCE;
          });
        }

        // Create temporary resolver context with current document's pointer index

        LOG.fine(() -> "Creating temporary RefSchema for local ref " + refToken.pointer() +
            " with " + localPointerIndex.size() + " local pointer entries");

        // For other references, use RefSchema with deferred resolution
        // Use a temporary resolver context that will be updated later
        return refSchema;
      }
    }

    if (schemaJson instanceof JsonBoolean bool) {
      return bool.value() ? AnySchema.INSTANCE : new NotSchema(AnySchema.INSTANCE);
    }

    if (!(schemaJson instanceof JsonObject obj)) {
      throw new IllegalArgumentException("Schema must be an object or boolean");
    }

    // Process definitions first and build pointer index
    JsonValue defsValue = obj.members().get("$defs");
    if (defsValue instanceof JsonObject defsObj) {
      trace("compile-defs", defsValue);
      for (var entry : defsObj.members().entrySet()) {
        String pointer = (basePointer == null || basePointer.isEmpty()) ? JsonSchema.SCHEMA_DEFS_POINTER + entry.getKey() : basePointer + "/$defs/" + entry.getKey();
        JsonSchema compiled = compileInternalWithContext(session, entry.getValue(), docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, sharedRoots, pointer);
        localPointerIndex.put(pointer, compiled);

        // Also index by $anchor if present
        if (entry.getValue() instanceof JsonObject defObj) {
          JsonValue anchorValue = defObj.members().get("$anchor");
          if (anchorValue instanceof JsonString anchorStr) {
            String anchorPointer = JsonSchema.SCHEMA_POINTER_ROOT + anchorStr.value();
            localPointerIndex.put(anchorPointer, compiled);
            LOG.finest(() -> "Indexed $anchor '" + anchorStr.value() + "' as " + anchorPointer);
          }
        }
      }
    }

    // Handle composition keywords
    JsonValue allOfValue = obj.members().get("allOf");
    if (allOfValue instanceof JsonArray allOfArr) {
      trace("compile-allof", allOfValue);
      List<JsonSchema> schemas = new ArrayList<>();
      for (JsonValue item : allOfArr.values()) {
        schemas.add(compileInternalWithContext(session, item, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, sharedRoots, basePointer));
      }
      return new AllOfSchema(schemas);
    }

    JsonValue anyOfValue = obj.members().get("anyOf");
    if (anyOfValue instanceof JsonArray anyOfArr) {
      trace("compile-anyof", anyOfValue);
      List<JsonSchema> schemas = new ArrayList<>();
      for (JsonValue item : anyOfArr.values()) {
        schemas.add(compileInternalWithContext(session, item, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, sharedRoots, basePointer));
      }
      return new AnyOfSchema(schemas);
    }

    JsonValue oneOfValue = obj.members().get("oneOf");
    if (oneOfValue instanceof JsonArray oneOfArr) {
      trace("compile-oneof", oneOfValue);
      List<JsonSchema> schemas = new ArrayList<>();
      for (JsonValue item : oneOfArr.values()) {
        schemas.add(compileInternalWithContext(session, item, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, sharedRoots, basePointer));
      }
      return new OneOfSchema(schemas);
    }

    // Handle if/then/else
    JsonValue ifValue = obj.members().get("if");
    if (ifValue != null) {
      trace("compile-conditional", obj);
      JsonSchema ifSchema = compileInternalWithContext(session, ifValue, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, sharedRoots, basePointer);
      JsonSchema thenSchema = null;
      JsonSchema elseSchema = null;

      JsonValue thenValue = obj.members().get("then");
      if (thenValue != null) {
        thenSchema = compileInternalWithContext(session, thenValue, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, sharedRoots, basePointer);
      }

      JsonValue elseValue = obj.members().get("else");
      if (elseValue != null) {
        elseSchema = compileInternalWithContext(session, elseValue, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, sharedRoots, basePointer);
      }

      return new ConditionalSchema(ifSchema, thenSchema, elseSchema);
    }

    // Handle const
    JsonValue constValue = obj.members().get("const");
    if (constValue != null) {
      return new ConstSchema(constValue);
    }

    // Handle not
    JsonValue notValue = obj.members().get("not");
    if (notValue != null) {
      JsonSchema inner = compileInternalWithContext(session, notValue, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, sharedRoots);
      return new NotSchema(inner);
    }

    // Detect keyword-based schema types for use in enum handling and fallback
    boolean hasObjectKeywords = obj.members().containsKey("properties")
        || obj.members().containsKey("required")
        || obj.members().containsKey("additionalProperties")
        || obj.members().containsKey("minProperties")
        || obj.members().containsKey("maxProperties")
        || obj.members().containsKey("patternProperties")
        || obj.members().containsKey("propertyNames")
        || obj.members().containsKey("dependentRequired")
        || obj.members().containsKey("dependentSchemas");

    boolean hasArrayKeywords = obj.members().containsKey("items")
        || obj.members().containsKey("minItems")
        || obj.members().containsKey("maxItems")
        || obj.members().containsKey("uniqueItems")
        || obj.members().containsKey("prefixItems")
        || obj.members().containsKey("contains")
        || obj.members().containsKey("minContains")
        || obj.members().containsKey("maxContains");

    boolean hasStringKeywords = obj.members().containsKey("pattern")
        || obj.members().containsKey("minLength")
        || obj.members().containsKey("maxLength")
        || obj.members().containsKey("format");

    // Handle enum early (before type-specific compilation)
    JsonValue enumValue = obj.members().get("enum");
    if (enumValue instanceof JsonArray enumArray) {
      // Build base schema from type or heuristics
      JsonSchema baseSchema;

      // If type is specified, use it; otherwise infer from keywords
      JsonValue typeValue = obj.members().get("type");
      if (typeValue instanceof JsonString typeStr) {
        baseSchema = switch (typeStr.value()) {
          case "object" ->
              compileObjectSchemaWithContext(session, obj, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, sharedRoots);
          case "array" ->
              compileArraySchemaWithContext(session, obj, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, sharedRoots);
          case "string" -> compileStringSchemaWithContext(session, obj);
          case "number", "integer" -> compileNumberSchemaWithContext(obj);
          case "boolean" -> new BooleanSchema();
          case "null" -> new NullSchema();
          default -> AnySchema.INSTANCE;
        };
      } else if (hasObjectKeywords) {
        baseSchema = compileObjectSchemaWithContext(session, obj, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, sharedRoots);
      } else if (hasArrayKeywords) {
        baseSchema = compileArraySchemaWithContext(session, obj, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, sharedRoots);
      } else if (hasStringKeywords) {
        baseSchema = compileStringSchemaWithContext(session, obj);
      } else {
        baseSchema = AnySchema.INSTANCE;
      }

      // Build enum values set
      Set<JsonValue> allowedValues = new LinkedHashSet<>(enumArray.values());

      return new EnumSchema(baseSchema, allowedValues);
    }

    // Handle type-based schemas
    JsonValue typeValue = obj.members().get("type");
    if (typeValue instanceof JsonString typeStr) {
      return switch (typeStr.value()) {
        case "object" ->
            compileObjectSchemaWithContext(session, obj, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, sharedRoots);
        case "array" ->
            compileArraySchemaWithContext(session, obj, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, sharedRoots);
        case "string" -> compileStringSchemaWithContext(session, obj);
        case "number" -> compileNumberSchemaWithContext(obj);
        case "integer" -> compileNumberSchemaWithContext(obj); // For now, treat integer as number
        case "boolean" -> new BooleanSchema();
        case "null" -> new NullSchema();
        default -> AnySchema.INSTANCE;
      };
    } else if (typeValue instanceof JsonArray typeArray) {
      // Handle type arrays: ["string", "null", ...] - treat as anyOf
      List<JsonSchema> typeSchemas = new ArrayList<>();
      for (JsonValue item : typeArray.values()) {
        if (item instanceof JsonString typeStr) {
          JsonSchema typeSchema = switch (typeStr.value()) {
            case "object" ->
                compileObjectSchemaWithContext(session, obj, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, sharedRoots);
            case "array" ->
                compileArraySchemaWithContext(session, obj, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, sharedRoots);
            case "string" -> compileStringSchemaWithContext(session, obj);
            case "number", "integer" -> compileNumberSchemaWithContext(obj);
            case "boolean" -> new BooleanSchema();
            case "null" -> new NullSchema();
            default -> AnySchema.INSTANCE;
          };
          typeSchemas.add(typeSchema);
        } else {
          throw new IllegalArgumentException("Type array must contain only strings");
        }
      }
      if (typeSchemas.isEmpty()) {
        return AnySchema.INSTANCE;
      } else if (typeSchemas.size() == 1) {
        return typeSchemas.getFirst();
      } else {
        return new AnyOfSchema(typeSchemas);
      }
    } else {
      if (hasObjectKeywords) {
        return compileObjectSchemaWithContext(session, obj, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, sharedRoots);
      } else if (hasArrayKeywords) {
        return compileArraySchemaWithContext(session, obj, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, sharedRoots);
      } else if (hasStringKeywords) {
        return compileStringSchemaWithContext(session, obj);
      }
    }

    return AnySchema.INSTANCE;
  }

  // Overload: preserve existing call sites with explicit resolverContext and resolutionStack
  private static JsonSchema compileInternalWithContext(Session session, JsonValue schemaJson, URI docUri,
                                                       Deque<JsonSchema.WorkItem> workStack, Set<URI> seenUris,
                                                       JsonSchema.ResolverContext resolverContext,
                                                       Map<String, JsonSchema> localPointerIndex,
                                                       Deque<String> resolutionStack,
                                                       Map<URI, JsonSchema.CompiledRoot> sharedRoots) {
    return compileInternalWithContext(session, schemaJson, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, sharedRoots, JsonSchema.SCHEMA_POINTER_ROOT);
  }

  /// Object schema compilation with context
  private static JsonSchema compileObjectSchemaWithContext(Session session, JsonObject obj, URI docUri, Deque<JsonSchema.WorkItem> workStack, Set<URI> seenUris, JsonSchema.ResolverContext resolverContext, Map<String, JsonSchema> localPointerIndex, Deque<String> resolutionStack, Map<URI, JsonSchema.CompiledRoot> sharedRoots) {
    LOG.finest(() -> "compileObjectSchemaWithContext: Starting with object: " + obj);
    Map<String, JsonSchema> properties = new LinkedHashMap<>();
    JsonValue propsValue = obj.members().get("properties");
    if (propsValue instanceof JsonObject propsObj) {
      LOG.finest(() -> "compileObjectSchemaWithContext: Processing properties: " + propsObj);
      for (var entry : propsObj.members().entrySet()) {
        LOG.finest(() -> "compileObjectSchemaWithContext: Compiling property '" + entry.getKey() + "': " + entry.getValue());
        // Push a context frame for this property
        // (Currently used for diagnostics and future pointer derivations)
        // Pop immediately after child compile
        JsonSchema propertySchema;
        // Best-effort: if we can see a CompileContext via resolverContext, skip; we don't expose it. So just compile.
        propertySchema = compileInternalWithContext(session, entry.getValue(), docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, sharedRoots);
        LOG.finest(() -> "compileObjectSchemaWithContext: Property '" + entry.getKey() + "' compiled to: " + propertySchema);
        properties.put(entry.getKey(), propertySchema);

        // Add to pointer index
        String pointer = JsonSchema.SCHEMA_POINTER_ROOT + JsonSchema.SCHEMA_PROPERTIES_SEGMENT + entry.getKey();
        localPointerIndex.put(pointer, propertySchema);
      }
    }

    Set<String> required = new LinkedHashSet<>();
    JsonValue reqValue = obj.members().get("required");
    if (reqValue instanceof JsonArray reqArray) {
      for (JsonValue item : reqArray.values()) {
        if (item instanceof JsonString str) {
          required.add(str.value());
        }
      }
    }

    JsonSchema additionalProperties = AnySchema.INSTANCE;
    JsonValue addPropsValue = obj.members().get("additionalProperties");
    if (addPropsValue instanceof JsonBoolean addPropsBool) {
      additionalProperties = addPropsBool.value() ? AnySchema.INSTANCE : BooleanSchema.FALSE;
    } else if (addPropsValue instanceof JsonObject addPropsObj) {
      additionalProperties = compileInternalWithContext(session, addPropsObj, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, sharedRoots);
    }

    // Handle patternProperties
    Map<Pattern, JsonSchema> patternProperties = null;
    JsonValue patternPropsValue = obj.members().get("patternProperties");
    if (patternPropsValue instanceof JsonObject patternPropsObj) {
      patternProperties = new LinkedHashMap<>();
      for (var entry : patternPropsObj.members().entrySet()) {
        String patternStr = entry.getKey();
        Pattern pattern = Pattern.compile(patternStr);
        JsonSchema schema = compileInternalWithContext(session, entry.getValue(), docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, sharedRoots);
        patternProperties.put(pattern, schema);
      }
    }

    // Handle propertyNames
    JsonSchema propertyNames = null;
    JsonValue propNamesValue = obj.members().get("propertyNames");
    if (propNamesValue != null) {
      propertyNames = compileInternalWithContext(session, propNamesValue, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, sharedRoots);
    }

    Integer minProperties = getInteger(obj, "minProperties");
    Integer maxProperties = getInteger(obj, "maxProperties");

    // Handle dependentRequired
    Map<String, Set<String>> dependentRequired = null;
    JsonValue depReqValue = obj.members().get("dependentRequired");
    if (depReqValue instanceof JsonObject depReqObj) {
      dependentRequired = new LinkedHashMap<>();
      for (var entry : depReqObj.members().entrySet()) {
        String triggerProp = entry.getKey();
        JsonValue depsValue = entry.getValue();
        if (depsValue instanceof JsonArray depsArray) {
          Set<String> requiredProps = new LinkedHashSet<>();
          for (JsonValue depItem : depsArray.values()) {
            if (depItem instanceof JsonString depStr) {
              requiredProps.add(depStr.value());
            } else {
              throw new IllegalArgumentException("dependentRequired values must be arrays of strings");
            }
          }
          dependentRequired.put(triggerProp, requiredProps);
        } else {
          throw new IllegalArgumentException("dependentRequired values must be arrays");
        }
      }
    }

    // Handle dependentSchemas
    Map<String, JsonSchema> dependentSchemas = null;
    JsonValue depSchValue = obj.members().get("dependentSchemas");
    if (depSchValue instanceof JsonObject depSchObj) {
      dependentSchemas = new LinkedHashMap<>();
      for (var entry : depSchObj.members().entrySet()) {
        String triggerProp = entry.getKey();
        JsonValue schemaValue = entry.getValue();
        JsonSchema schema;
        if (schemaValue instanceof JsonBoolean boolValue) {
          schema = boolValue.value() ? AnySchema.INSTANCE : BooleanSchema.FALSE;
        } else {
          schema = compileInternalWithContext(session, schemaValue, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, sharedRoots);
        }
        dependentSchemas.put(triggerProp, schema);
      }
    }

    return new ObjectSchema(properties, required, additionalProperties, minProperties, maxProperties, patternProperties, propertyNames, dependentRequired, dependentSchemas);
  }

  /// Array schema compilation with context
  private static JsonSchema compileArraySchemaWithContext(Session session, JsonObject obj, URI docUri, Deque<JsonSchema.WorkItem> workStack, Set<URI> seenUris, JsonSchema.ResolverContext resolverContext, Map<String, JsonSchema> localPointerIndex, Deque<String> resolutionStack, Map<URI, JsonSchema.CompiledRoot> sharedRoots) {
    JsonSchema items = AnySchema.INSTANCE;
    JsonValue itemsValue = obj.members().get("items");
    if (itemsValue != null) {
      items = compileInternalWithContext(session, itemsValue, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, sharedRoots);
    }

    // Parse prefixItems (tuple validation)
    List<JsonSchema> prefixItems = null;
    JsonValue prefixItemsVal = obj.members().get("prefixItems");
    if (prefixItemsVal instanceof JsonArray arr) {
      prefixItems = new ArrayList<>(arr.values().size());
      for (JsonValue v : arr.values()) {
        prefixItems.add(compileInternalWithContext(session, v, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, sharedRoots));
      }
      prefixItems = List.copyOf(prefixItems);
    }

    // Parse contains schema
    JsonSchema contains = null;
    JsonValue containsVal = obj.members().get("contains");
    if (containsVal != null) {
      contains = compileInternalWithContext(session, containsVal, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, sharedRoots);
    }

    // Parse minContains / maxContains
    Integer minContains = getInteger(obj, "minContains");
    Integer maxContains = getInteger(obj, "maxContains");

    Integer minItems = getInteger(obj, "minItems");
    Integer maxItems = getInteger(obj, "maxItems");
    Boolean uniqueItems = getBoolean(obj, "uniqueItems");

    return new ArraySchema(items, minItems, maxItems, uniqueItems, prefixItems, contains, minContains, maxContains);
  }

  /// String schema compilation with context
  private static JsonSchema compileStringSchemaWithContext(Session session, JsonObject obj) {
    Integer minLength = getInteger(obj, "minLength");
    Integer maxLength = getInteger(obj, "maxLength");

    Pattern pattern = null;
    JsonValue patternValue = obj.members().get("pattern");
    if (patternValue instanceof JsonString patternStr) {
      pattern = Pattern.compile(patternStr.value());
    }

    // Handle format keyword
    FormatValidator formatValidator = null;
    boolean assertFormats = session.currentJsonSchemaOptions != null && session.currentJsonSchemaOptions.assertFormats();

    if (assertFormats) {
      JsonValue formatValue = obj.members().get("format");
      if (formatValue instanceof JsonString formatStr) {
        String formatName = formatStr.value();
        formatValidator = Format.byName(formatName);
        if (formatValidator == null) {
          LOG.fine("Unknown format: " + formatName);
        }
      }
    }

    return new StringSchema(minLength, maxLength, pattern, formatValidator, assertFormats);
  }

  /// Number schema compilation with context
  private static JsonSchema compileNumberSchemaWithContext(JsonObject obj) {
    BigDecimal minimum = getBigDecimal(obj, "minimum");
    BigDecimal maximum = getBigDecimal(obj, "maximum");
    BigDecimal multipleOf = getBigDecimal(obj, "multipleOf");
    Boolean exclusiveMinimum = getBoolean(obj, "exclusiveMinimum");
    Boolean exclusiveMaximum = getBoolean(obj, "exclusiveMaximum");

    // Handle numeric exclusiveMinimum/exclusiveMaximum (2020-12 spec)
    BigDecimal exclusiveMinValue = getBigDecimal(obj, "exclusiveMinimum");
    BigDecimal exclusiveMaxValue = getBigDecimal(obj, "exclusiveMaximum");

    // Normalize: if numeric exclusives are present, convert to boolean form
    if (exclusiveMinValue != null) {
      minimum = exclusiveMinValue;
      exclusiveMinimum = true;
    }
    if (exclusiveMaxValue != null) {
      maximum = exclusiveMaxValue;
      exclusiveMaximum = true;
    }

    return new NumberSchema(minimum, maximum, multipleOf, exclusiveMinimum, exclusiveMaximum);
  }

  private static Integer getInteger(JsonObject obj, String key) {
    JsonValue value = obj.members().get(key);
    if (value instanceof JsonNumber num) {
      Number n = num.toNumber();
      if (n instanceof Integer i) return i;
      if (n instanceof Long l) return l.intValue();
      if (n instanceof BigDecimal bd) return bd.intValue();
    }
    return null;
  }

  private static Boolean getBoolean(JsonObject obj, String key) {
    JsonValue value = obj.members().get(key);
    if (value instanceof JsonBoolean bool) {
      return bool.value();
    }
    return null;
  }

  private static BigDecimal getBigDecimal(JsonObject obj, String key) {
    JsonValue value = obj.members().get(key);
    if (value instanceof JsonNumber num) {
      Number n = num.toNumber();
      if (n instanceof BigDecimal) return (BigDecimal) n;
      if (n instanceof BigInteger) return new BigDecimal((BigInteger) n);
      return BigDecimal.valueOf(n.doubleValue());
    }
    return null;
  }
}
