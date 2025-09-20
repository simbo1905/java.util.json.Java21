/// Copyright (c) 2025 Simon Massey
///
/// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
///
/// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
///
/// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/// JSON Schema public API entry point
///
/// This class provides the public API for compiling and validating schemas
/// while delegating implementation details to package-private classes
///
/// ## Usage
/// ```java
/// // Compile schema once (thread-safe, reusable)
/// JsonSchema schema = JsonSchema.compile(Json.parse(schemaJson));
///
/// // Validate JSON documents
/// ValidationResult result = schema.validate(Json.parse(jsonDoc));
///
/// if (!result.valid()){
///     for (var error : result.errors()){
///         System.out.println(error.path() + ": " + error.message());
///}
///}
///```
public sealed interface JsonSchema
    permits JsonSchema.Nothing,
    JsonSchema.ObjectSchema,
    JsonSchema.ArraySchema,
    JsonSchema.StringSchema,
    JsonSchema.NumberSchema,
    JsonSchema.BooleanSchema,
    JsonSchema.NullSchema,
    JsonSchema.AnySchema,
    JsonSchema.RefSchema,
    JsonSchema.AllOfSchema,
    JsonSchema.AnyOfSchema,
    JsonSchema.OneOfSchema,
    JsonSchema.ConditionalSchema,
    JsonSchema.ConstSchema,
    JsonSchema.NotSchema,
    JsonSchema.RootRef,
    JsonSchema.EnumSchema {

  Logger LOG = Logger.getLogger(JsonSchema.class.getName());

  // Public constants for common JSON Pointer fragments used in schemas
  public static final String SCHEMA_DEFS_POINTER = "#/$defs/";
  public static final String SCHEMA_DEFS_SEGMENT = "/$defs/";
  public static final String SCHEMA_PROPERTIES_SEGMENT = "/properties/";
  public static final String SCHEMA_POINTER_PREFIX = "#/";
  public static final String SCHEMA_POINTER_ROOT = "#";
  public static final String SCHEMA_ROOT_POINTER = "#/";

  /// Prevents external implementations, ensuring all schema types are inner records
  enum Nothing implements JsonSchema {
    ;  // Empty enum - just used as a sealed interface permit

    @Override
    public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
      LOG.severe(() -> "ERROR: Nothing enum validateAt called - this should never happen");
      throw new UnsupportedOperationException("Nothing enum should not be used for validation");
    }
  }

  /// Options for schema compilation
  ///
  /// @param assertFormats whether to enable format assertion validation
  record Options(boolean assertFormats) {
    /// Default options with format assertion disabled
    static final Options DEFAULT = new Options(false);
  }

  /// Compile-time options controlling remote resolution and caching
  record CompileOptions(
      UriResolver uriResolver,
      RemoteFetcher remoteFetcher,
      RefRegistry refRegistry,
      FetchPolicy fetchPolicy
  ) {
    static final CompileOptions DEFAULT =
        new CompileOptions(UriResolver.defaultResolver(), RemoteFetcher.disallowed(), RefRegistry.disallowed(), FetchPolicy.defaults());

    static CompileOptions remoteDefaults(RemoteFetcher fetcher) {
      Objects.requireNonNull(fetcher, "fetcher");
      return new CompileOptions(UriResolver.defaultResolver(), fetcher, RefRegistry.inMemory(), FetchPolicy.defaults());
    }

    CompileOptions withRemoteFetcher(RemoteFetcher fetcher) {
      Objects.requireNonNull(fetcher, "fetcher");
      return new CompileOptions(uriResolver, fetcher, refRegistry, fetchPolicy);
    }

    CompileOptions withRefRegistry(RefRegistry registry) {
      Objects.requireNonNull(registry, "registry");
      return new CompileOptions(uriResolver, remoteFetcher, registry, fetchPolicy);
    }

    CompileOptions withFetchPolicy(FetchPolicy policy) {
      Objects.requireNonNull(policy, "policy");
      return new CompileOptions(uriResolver, remoteFetcher, refRegistry, policy);
    }
  }


  /// URI resolver responsible for base resolution and normalization
  interface UriResolver {

    static UriResolver defaultResolver() {
      return DefaultUriResolver.INSTANCE;
    }

    enum DefaultUriResolver implements UriResolver {
      INSTANCE

    }
  }

  /// Remote fetcher SPI for loading external schema documents
  interface RemoteFetcher {
    FetchResult fetch(java.net.URI uri, FetchPolicy policy) throws RemoteResolutionException;

    static RemoteFetcher disallowed() {
      return (uri, policy) -> {
        LOG.severe(() -> "ERROR: Remote fetching disabled but requested for URI: " + uri);
        throw new RemoteResolutionException(
            Objects.requireNonNull(uri, "uri"),
            RemoteResolutionException.Reason.POLICY_DENIED,
            "Remote fetching is disabled"
        );
      };
    }

    record FetchResult(JsonValue document, long byteSize, Optional<java.time.Duration> elapsed) {
      public FetchResult {
        Objects.requireNonNull(document, "document");
        if (byteSize < 0L) {
          throw new IllegalArgumentException("byteSize must be >= 0");
        }
      }
    }
  }

  /// Registry caching compiled schemas by canonical URI + fragment
  interface RefRegistry {

    static RefRegistry disallowed() {
      return new RefRegistry() {

      };
    }

    static RefRegistry inMemory() {
      return new InMemoryRefRegistry();
    }

    final class InMemoryRefRegistry implements RefRegistry {

    }
  }

  /// Fetch policy settings controlling network guardrails
  record FetchPolicy(
      Set<String> allowedSchemes,
      long maxDocumentBytes,
      long maxTotalBytes,
      java.time.Duration timeout,
      int maxRedirects,
      int maxDocuments,
      int maxDepth
  ) {
    public FetchPolicy {
      Objects.requireNonNull(allowedSchemes, "allowedSchemes");
      Objects.requireNonNull(timeout, "timeout");
      if (allowedSchemes.isEmpty()) {
        throw new IllegalArgumentException("allowedSchemes must not be empty");
      }
      if (maxDocumentBytes <= 0L) {
        throw new IllegalArgumentException("maxDocumentBytes must be > 0");
      }
      if (maxTotalBytes <= 0L) {
        throw new IllegalArgumentException("maxTotalBytes must be > 0");
      }
      if (maxRedirects < 0) {
        throw new IllegalArgumentException("maxRedirects must be >= 0");
      }
      if (maxDocuments <= 0) {
        throw new IllegalArgumentException("maxDocuments must be > 0");
      }
      if (maxDepth <= 0) {
        throw new IllegalArgumentException("maxDepth must be > 0");
      }
    }

    static FetchPolicy defaults() {
      return new FetchPolicy(Set.of("http", "https", "file"), 1_048_576L, 8_388_608L, java.time.Duration.ofSeconds(5), 3, 64, 64);
    }

    FetchPolicy withAllowedSchemes(Set<String> schemes) {
      Objects.requireNonNull(schemes, "schemes");
      return new FetchPolicy(Set.copyOf(schemes), maxDocumentBytes, maxTotalBytes, timeout, maxRedirects, maxDocuments, maxDepth);
    }

    FetchPolicy withMaxDocumentBytes() {
      return new FetchPolicy(allowedSchemes, 10, maxTotalBytes, timeout, maxRedirects, maxDocuments, maxDepth);
    }

    FetchPolicy withTimeout(java.time.Duration newTimeout) {
      Objects.requireNonNull(newTimeout, "newTimeout");
      return new FetchPolicy(allowedSchemes, maxDocumentBytes, maxTotalBytes, newTimeout, maxRedirects, maxDocuments, maxDepth);
    }
  }

  /// Exception signalling remote resolution failures with typed reasons
  final class RemoteResolutionException extends RuntimeException {
    private final java.net.URI uri;
    private final Reason reason;

    RemoteResolutionException(java.net.URI uri, Reason reason, String message) {
      super(message);
      this.uri = Objects.requireNonNull(uri, "uri");
      this.reason = Objects.requireNonNull(reason, "reason");
    }

    RemoteResolutionException(java.net.URI uri, Reason reason, String message, Throwable cause) {
      super(message, cause);
      this.uri = Objects.requireNonNull(uri, "uri");
      this.reason = Objects.requireNonNull(reason, "reason");
    }

    public java.net.URI uri() {
      return uri;
    }

    @SuppressWarnings("ClassEscapesDefinedScope")
    public Reason reason() {
      return reason;
    }

    enum Reason {
      NETWORK_ERROR,
      POLICY_DENIED,
      NOT_FOUND,
      POINTER_MISSING,
      PAYLOAD_TOO_LARGE,
      TIMEOUT
    }
  }

  /// Factory method to create schema from JSON Schema document
  ///
  /// @param schemaJson JSON Schema document as JsonValue
  /// @return Immutable JsonSchema instance
  /// @throws IllegalArgumentException if schema is invalid
  static JsonSchema compile(JsonValue schemaJson) {
    Objects.requireNonNull(schemaJson, "schemaJson");
    LOG.fine(() -> "compile: Starting schema compilation with default options, schema type: " + schemaJson.getClass().getSimpleName());
    JsonSchema result = compile(schemaJson, Options.DEFAULT, CompileOptions.DEFAULT);
    LOG.fine(() -> "compile: Completed schema compilation, result type: " + result.getClass().getSimpleName());
    return result;
  }

  /// Factory method to create schema from JSON Schema document with options
  ///
  /// @param schemaJson JSON Schema document as JsonValue
  /// @param options compilation options
  /// @return Immutable JsonSchema instance
  /// @throws IllegalArgumentException if schema is invalid
  static JsonSchema compile(JsonValue schemaJson, Options options) {
    Objects.requireNonNull(schemaJson, "schemaJson");
    Objects.requireNonNull(options, "options");
    LOG.fine(() -> "compile: Starting schema compilation with custom options, schema type: " + schemaJson.getClass().getSimpleName());
    JsonSchema result = compile(schemaJson, options, CompileOptions.DEFAULT);
    LOG.fine(() -> "compile: Completed schema compilation with custom options, result type: " + result.getClass().getSimpleName());
    return result;
  }

  /// Factory method to create schema with explicit compile options
  static JsonSchema compile(JsonValue schemaJson, Options options, CompileOptions compileOptions) {
    Objects.requireNonNull(schemaJson, "schemaJson");
    Objects.requireNonNull(options, "options");
    Objects.requireNonNull(compileOptions, "compileOptions");
    LOG.fine(() -> "compile: Starting schema compilation with initial URI: " + java.net.URI.create("urn:inmemory:root"));
    LOG.fine(() -> "compile: Starting schema compilation with full options, schema type: " + schemaJson.getClass().getSimpleName() +
        ", options.assertFormats=" + options.assertFormats() + ", compileOptions.remoteFetcher=" + compileOptions.remoteFetcher().getClass().getSimpleName());
    LOG.fine(() -> "compile: fetch policy allowedSchemes=" + compileOptions.fetchPolicy().allowedSchemes());

    // Early policy enforcement for root-level remote $ref to avoid unnecessary work
    if (schemaJson instanceof JsonObject rootObj) {
      JsonValue refVal = rootObj.members().get("$ref");
      if (refVal instanceof JsonString refStr) {
        try {
          java.net.URI refUri = java.net.URI.create(refStr.value());
          String scheme = refUri.getScheme();
          if (scheme != null && !compileOptions.fetchPolicy().allowedSchemes().contains(scheme)) {
            throw new RemoteResolutionException(refUri, RemoteResolutionException.Reason.POLICY_DENIED,
                "Scheme not allowed by policy: " + refUri);
          }
        } catch (IllegalArgumentException ignore) {
          // Not a URI, ignore - normal compilation will handle it
        }
      }
    }

    // Build resolver context using new MVF work-stack architecture
    ResolverContext context = initResolverContext(java.net.URI.create("urn:inmemory:root"), schemaJson, compileOptions);
    LOG.fine(() -> "compile: Created resolver context with roots.size=0, base uri: " + java.net.URI.create("urn:inmemory:root"));

    // Compile using work-stack architecture (thread options + compileOptions)
    CompiledRegistry registry = compileWorkStack(
        schemaJson,
        java.net.URI.create("urn:inmemory:root"),
        context,
        options,
        compileOptions
    );
    JsonSchema result = registry.entry().schema();

    // Update resolver context to use full compiled registry for remote references
    ResolverContext fullContext = createResolverContextFromRegistry(registry);
    final int rootCount = fullContext.roots().size();
    final var updatedResult = updateSchemaWithFullContext(result, fullContext);

    // Compile-time validation for root-level remote $ref pointer existence
    if (updatedResult instanceof RefSchema ref) {
      if (ref.refToken() instanceof RefToken.RemoteRef remoteRef) {
        String frag = remoteRef.pointer();
        if (frag != null && !frag.isEmpty()) {
          try {
            // Attempt resolution now to surface POINTER_MISSING during compile
            fullContext.resolve(ref.refToken());
          } catch (IllegalArgumentException e) {
            throw new RemoteResolutionException(
                remoteRef.targetUri(),
                RemoteResolutionException.Reason.POINTER_MISSING,
                "Pointer not found in remote document: " + remoteRef.targetUri(),
                e
            );
          }
        }
      }
    }

    LOG.fine(() -> "compile: Completed schema compilation, total roots compiled: " + rootCount);
    LOG.fine(() -> "compile: Completed schema compilation with full options, result type: " + updatedResult.getClass().getSimpleName());
    return updatedResult;
  }

  /// Normalize URI for dedup correctness
  static java.net.URI normalizeUri(java.net.URI baseUri, String refString) {
    LOG.fine(() -> "normalizeUri: entry with base=" + baseUri + ", refString=" + refString);
    LOG.finest(() -> "normalizeUri: baseUri object=" + baseUri + ", scheme=" + baseUri.getScheme() + ", host=" + baseUri.getHost() + ", path=" + baseUri.getPath());
    try {
      java.net.URI refUri = java.net.URI.create(refString);
      LOG.finest(() -> "normalizeUri: created refUri=" + refUri + ", scheme=" + refUri.getScheme() + ", host=" + refUri.getHost() + ", path=" + refUri.getPath());
      java.net.URI resolved = baseUri.resolve(refUri);
      LOG.finest(() -> "normalizeUri: resolved URI=" + resolved + ", scheme=" + resolved.getScheme() + ", host=" + resolved.getHost() + ", path=" + resolved.getPath());
      java.net.URI normalized = resolved.normalize();
      LOG.finer(() -> "normalizeUri: normalized result=" + normalized);
      LOG.finest(() -> "normalizeUri: final normalized URI=" + normalized + ", scheme=" + normalized.getScheme() + ", host=" + normalized.getHost() + ", path=" + normalized.getPath());
      return normalized;
    } catch (IllegalArgumentException e) {
      LOG.severe(() -> "ERROR: normalizeUri failed for refString=" + refString + ", baseUri=" + baseUri);
      throw new IllegalArgumentException("Invalid URI reference: " + refString);
    }
  }

  /// Initialize resolver context for compile-time
  static ResolverContext initResolverContext(java.net.URI initialUri, JsonValue initialJson, CompileOptions compileOptions) {
    LOG.fine(() -> "initResolverContext: created context for initialUri=" + initialUri);
    LOG.finest(() -> "initResolverContext: initialJson object=" + initialJson + ", type=" + initialJson.getClass().getSimpleName() + ", toString=" + initialJson);
    LOG.finest(() -> "initResolverContext: compileOptions object=" + compileOptions + ", remoteFetcher=" + compileOptions.remoteFetcher().getClass().getSimpleName());
    Map<java.net.URI, CompiledRoot> emptyRoots = new HashMap<>();
    Map<String, JsonSchema> emptyPointerIndex = new HashMap<>();
    ResolverContext context = new ResolverContext(emptyRoots, emptyPointerIndex, AnySchema.INSTANCE);
    LOG.finest(() -> "initResolverContext: created context object=" + context + ", roots.size=" + context.roots().size() + ", localPointerIndex.size=" + context.localPointerIndex().size());
    return context;
  }

  /// Core work-stack compilation loop
  static CompiledRegistry compileWorkStack(JsonValue initialJson,
                                           java.net.URI initialUri,
                                           ResolverContext context,
                                           Options options,
                                           CompileOptions compileOptions) {
    LOG.fine(() -> "compileWorkStack: starting work-stack loop with initialUri=" + initialUri);
    LOG.finest(() -> "compileWorkStack: initialJson object=" + initialJson + ", type=" + initialJson.getClass().getSimpleName() + ", content=" + initialJson);
    LOG.finest(() -> "compileWorkStack: initialUri object=" + initialUri + ", scheme=" + initialUri.getScheme() + ", host=" + initialUri.getHost() + ", path=" + initialUri.getPath());

    // Work stack (LIFO) for documents to compile
    Deque<java.net.URI> workStack = new ArrayDeque<>();
    Map<java.net.URI, CompiledRoot> built = new LinkedHashMap<>();
    Set<java.net.URI> active = new HashSet<>();

    LOG.finest(() -> "compileWorkStack: initialized workStack=" + workStack + ", built=" + built + ", active=" + active);

    // Push initial document
    workStack.push(initialUri);
    LOG.finer(() -> "compileWorkStack: pushed initial URI to work stack: " + initialUri);
    LOG.finest(() -> "compileWorkStack: workStack after push=" + workStack + ", contents=" + workStack.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(", ", "[", "]")));

    int iterationCount = 0;
    while (!workStack.isEmpty()) {
      iterationCount++;
      final int finalIterationCount = iterationCount;
      final int workStackSize = workStack.size();
      final int builtSize = built.size();
      final int activeSize = active.size();
      LOG.fine(() -> "compileWorkStack: iteration " + finalIterationCount + ", workStack.size=" + workStackSize + ", built.size=" + builtSize + ", active.size=" + activeSize);
      LOG.finest(() -> "compileWorkStack: workStack contents=" + workStack.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(", ", "[", "]")));
      LOG.finest(() -> "compileWorkStack: built map keys=" + built.keySet() + ", values=" + built.values());
      LOG.finest(() -> "compileWorkStack: active set=" + active);

      java.net.URI currentUri = workStack.pop();
      LOG.finer(() -> "compileWorkStack: popped URI from work stack: " + currentUri);
      LOG.finest(() -> "compileWorkStack: workStack after pop=" + workStack + ", contents=" + workStack.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(", ", "[", "]")));

      // Check for cycles
      detectAndThrowCycle(active, currentUri, "compile-time remote ref cycle");

      // Skip if already compiled
      if (built.containsKey(currentUri)) {
        LOG.finer(() -> "compileWorkStack: URI already compiled, skipping: " + currentUri);
        LOG.finest(() -> "compileWorkStack: built map already contains key=" + currentUri);
        continue;
      }

      final java.net.URI finalCurrentUri = currentUri;
      final Map<java.net.URI, CompiledRoot> finalBuilt = built;
      final Deque<java.net.URI> finalWorkStack = workStack;

      active.add(currentUri);
      LOG.finest(() -> "compileWorkStack: added URI to active set, active now=" + active);
      try {
        // Fetch document if needed
        JsonValue documentJson = fetchIfNeeded(currentUri, initialUri, initialJson, context, compileOptions);
        LOG.finer(() -> "compileWorkStack: fetched document for URI: " + currentUri + ", json type: " + documentJson.getClass().getSimpleName());
        LOG.finest(() -> "compileWorkStack: fetched documentJson object=" + documentJson + ", type=" + documentJson.getClass().getSimpleName() + ", content=" + documentJson);

        // Build root schema for this document
        JsonSchema rootSchema = buildRoot(documentJson, currentUri, context, (refToken) -> {
          LOG.finest(() -> "compileWorkStack: discovered ref token object=" + refToken + ", class=" + refToken.getClass().getSimpleName());
          if (refToken instanceof RefToken.RemoteRef remoteRef) {
            LOG.finest(() -> "compileWorkStack: processing RemoteRef object=" + remoteRef + ", base=" + remoteRef.baseUri() + ", target=" + remoteRef.targetUri());
            java.net.URI targetDocUri = normalizeUri(finalCurrentUri, remoteRef.targetUri().toString());
            boolean scheduled = scheduleRemoteIfUnseen(finalWorkStack, finalBuilt, targetDocUri);
            LOG.finer(() -> "compileWorkStack: remote ref scheduled=" + scheduled + ", target=" + targetDocUri);
          }
        }, built, options, compileOptions);
        LOG.finest(() -> "compileWorkStack: built rootSchema object=" + rootSchema + ", class=" + rootSchema.getClass().getSimpleName());
      } finally {
        active.remove(currentUri);
        LOG.finest(() -> "compileWorkStack: removed URI from active set, active now=" + active);
      }
    }

    // Freeze roots into immutable registry (preserve entry root as initialUri)
    CompiledRegistry registry = freezeRoots(built, initialUri);
    LOG.fine(() -> "compileWorkStack: completed work-stack loop, total roots: " + registry.roots().size());
    LOG.finest(() -> "compileWorkStack: final registry object=" + registry + ", entry=" + registry.entry() + ", roots.size=" + registry.roots().size());
    return registry;
  }

  /// Fetch document if needed (primary vs remote)
  static JsonValue fetchIfNeeded(java.net.URI docUri,
                                 java.net.URI initialUri,
                                 JsonValue initialJson,
                                 ResolverContext context,
                                 CompileOptions compileOptions) {
    LOG.fine(() -> "fetchIfNeeded: docUri=" + docUri + ", initialUri=" + initialUri);
    LOG.finest(() -> "fetchIfNeeded: docUri object=" + docUri + ", scheme=" + docUri.getScheme() + ", host=" + docUri.getHost() + ", path=" + docUri.getPath());
    LOG.finest(() -> "fetchIfNeeded: initialUri object=" + initialUri + ", scheme=" + initialUri.getScheme() + ", host=" + initialUri.getHost() + ", path=" + initialUri.getPath());
    LOG.finest(() -> "fetchIfNeeded: initialJson object=" + initialJson + ", type=" + initialJson.getClass().getSimpleName() + ", content=" + initialJson);
    LOG.finest(() -> "fetchIfNeeded: context object=" + context + ", roots.size=" + context.roots().size() + ", localPointerIndex.size=" + context.localPointerIndex().size());

    if (docUri.equals(initialUri)) {
      LOG.finer(() -> "fetchIfNeeded: using initial JSON for primary document");
      LOG.finest(() -> "fetchIfNeeded: returning initialJson object=" + initialJson);
      return initialJson;
    }

    // MVF: Fetch remote document using RemoteFetcher from compile options
    LOG.finer(() -> "fetchIfNeeded: fetching remote document: " + docUri);
    try {
      // Get the base URI without fragment for document fetching
      String fragment = docUri.getFragment();
      java.net.URI docUriWithoutFragment = fragment != null ?
          java.net.URI.create(docUri.toString().substring(0, docUri.toString().indexOf('#'))) :
          docUri;

      LOG.finest(() -> "fetchIfNeeded: document URI without fragment: " + docUriWithoutFragment);

      // Enforce allowed schemes
      String scheme = docUriWithoutFragment.getScheme();
      if (scheme == null || !compileOptions.fetchPolicy().allowedSchemes().contains(scheme)) {
        throw new RemoteResolutionException(
            docUriWithoutFragment,
            RemoteResolutionException.Reason.POLICY_DENIED,
            "Scheme not allowed by policy: " + scheme
        );
      }

      // Prefer a local file mapping for tests when using file:// URIs
      java.net.URI fetchUri = docUriWithoutFragment;
      if ("file".equalsIgnoreCase(scheme)) {
        String base = System.getProperty("json.schema.test.resources", "src/test/resources");
        String path = fetchUri.getPath();
        if (path != null && path.startsWith("/")) path = path.substring(1);
        java.nio.file.Path abs = java.nio.file.Paths.get(base, path).toAbsolutePath();
        java.net.URI alt = abs.toUri();
        fetchUri = alt;
        LOG.fine(() -> "fetchIfNeeded: Using file mapping for fetch: " + alt + " (original=" + docUriWithoutFragment + ")");
      }

      // Fetch via provided RemoteFetcher to ensure consistent policy/normalization
      RemoteFetcher.FetchResult fetchResult;
      try {
        fetchResult = compileOptions.remoteFetcher().fetch(fetchUri, compileOptions.fetchPolicy());
      } catch (RemoteResolutionException e1) {
        // On mapping miss, retry original URI once
        if (!fetchUri.equals(docUriWithoutFragment)) {
          fetchResult = compileOptions.remoteFetcher().fetch(docUriWithoutFragment, compileOptions.fetchPolicy());
        } else {
          throw e1;
        }
      }
      JsonValue fetchedDocument = fetchResult.document();

      LOG.fine(() -> "fetchIfNeeded: successfully fetched remote document: " + docUriWithoutFragment + ", document type: " + fetchedDocument.getClass().getSimpleName());
      LOG.finest(() -> "fetchIfNeeded: returning fetched document object=" + fetchedDocument + ", type=" + fetchedDocument.getClass().getSimpleName() + ", content=" + fetchedDocument);
      return fetchedDocument;

    } catch (Exception e) {
      LOG.severe(() -> "ERROR: fetchIfNeeded failed to fetch remote document: " + docUri + ", error: " + e.getMessage());
      throw new RemoteResolutionException(docUri, RemoteResolutionException.Reason.NETWORK_ERROR,
          "Failed to fetch remote document: " + docUri, e);
    }
  }

  

  /// Build root schema for a document
  static JsonSchema buildRoot(JsonValue documentJson,
                              java.net.URI docUri,
                              ResolverContext context,
                              java.util.function.Consumer<RefToken> onRefDiscovered,
                              Map<java.net.URI, CompiledRoot> built,
                              Options options,
                              CompileOptions compileOptions) {
    LOG.fine(() -> "buildRoot: entry for docUri=" + docUri);
    LOG.finer(() -> "buildRoot: document type=" + documentJson.getClass().getSimpleName());
    LOG.finest(() -> "buildRoot: documentJson object=" + documentJson + ", type=" + documentJson.getClass().getSimpleName() + ", content=" + documentJson);
    LOG.finest(() -> "buildRoot: docUri object=" + docUri + ", scheme=" + docUri.getScheme() + ", host=" + docUri.getHost() + ", path=" + docUri.getPath());
    LOG.finest(() -> "buildRoot: context object=" + context + ", roots.size=" + context.roots().size() + ", localPointerIndex.size=" + context.localPointerIndex().size());
    LOG.finest(() -> "buildRoot: onRefDiscovered consumer=" + onRefDiscovered);

    // MVF: Use SchemaCompiler.compileBundle to properly integrate with work-stack architecture
    // This ensures remote refs are discovered and scheduled properly
    LOG.finer(() -> "buildRoot: using MVF compileBundle for proper work-stack integration");

    // Use the new MVF compileBundle method that properly handles remote refs
    CompilationBundle bundle = SchemaCompiler.compileBundle(
        documentJson,
        options,
        compileOptions
    );

    // Get the compiled schema from the bundle
    JsonSchema schema = bundle.entry().schema();
    LOG.finest(() -> "buildRoot: compiled schema object=" + schema + ", class=" + schema.getClass().getSimpleName());

    // Register all compiled roots from the bundle into the global built map
    LOG.finest(() -> "buildRoot: registering " + bundle.all().size() + " compiled roots from bundle into global registry");
    for (CompiledRoot compiledRoot : bundle.all()) {
      java.net.URI rootUri = compiledRoot.docUri();
      LOG.finest(() -> "buildRoot: registering compiled root for URI: " + rootUri);
      built.put(rootUri, compiledRoot);
      LOG.fine(() -> "buildRoot: registered compiled root for URI: " + rootUri);
    }

    LOG.fine(() -> "buildRoot: built registry now has " + built.size() + " roots: " + built.keySet());

    // Process any discovered refs from the compilation
    // The compileBundle method should have already processed remote refs through the work stack
    LOG.finer(() -> "buildRoot: MVF compilation completed, work stack processed remote refs");
    LOG.finer(() -> "buildRoot: completed for docUri=" + docUri + ", schema type=" + schema.getClass().getSimpleName());
    return schema;
  }

  /// Tag $ref token as LOCAL or REMOTE
  sealed interface RefToken permits RefToken.LocalRef, RefToken.RemoteRef {

    /// JSON pointer (without enforcing leading '#') for diagnostics/index lookups
    String pointer();

    record LocalRef(String pointerOrAnchor) implements RefToken {

      @Override
      public String pointer() {
        return pointerOrAnchor;
      }
    }

    record RemoteRef(java.net.URI baseUri, java.net.URI targetUri) implements RefToken {

      @Override
      public String pointer() {
        String fragment = targetUri.getFragment();
        return fragment != null ? fragment : "";
      }
    }
  }

  /// Schedule remote document for compilation if not seen before
  static boolean scheduleRemoteIfUnseen(Deque<java.net.URI> workStack, Map<java.net.URI, CompiledRoot> built, java.net.URI targetDocUri) {
    LOG.finer(() -> "scheduleRemoteIfUnseen: target=" + targetDocUri + ", workStack.size=" + workStack.size() + ", built.size=" + built.size());
    LOG.finest(() -> "scheduleRemoteIfUnseen: targetDocUri object=" + targetDocUri + ", scheme=" + targetDocUri.getScheme() + ", host=" + targetDocUri.getHost() + ", path=" + targetDocUri.getPath());
    LOG.finest(() -> "scheduleRemoteIfUnseen: workStack object=" + workStack + ", contents=" + workStack.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(", ", "[", "]")));
    LOG.finest(() -> "scheduleRemoteIfUnseen: built map object=" + built + ", keys=" + built.keySet() + ", size=" + built.size());

    // Check if already built or already in work stack
    boolean alreadyBuilt = built.containsKey(targetDocUri);
    boolean inWorkStack = workStack.contains(targetDocUri);
    LOG.finest(() -> "scheduleRemoteIfUnseen: alreadyBuilt=" + alreadyBuilt + ", inWorkStack=" + inWorkStack);

    if (alreadyBuilt || inWorkStack) {
      LOG.finer(() -> "scheduleRemoteIfUnseen: already seen, skipping");
      LOG.finest(() -> "scheduleRemoteIfUnseen: skipping targetDocUri=" + targetDocUri);
      return false;
    }

    // Add to work stack
    workStack.push(targetDocUri);
    LOG.finer(() -> "scheduleRemoteIfUnseen: scheduled remote document: " + targetDocUri);
    LOG.finest(() -> "scheduleRemoteIfUnseen: workStack after push=" + workStack + ", contents=" + workStack.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(", ", "[", "]")));
    return true;
  }

  /// Detect and throw on compile-time cycles
  static void detectAndThrowCycle(Set<java.net.URI> active, java.net.URI docUri, String pathTrail) {
    LOG.finest(() -> "detectAndThrowCycle: active set=" + active + ", docUri=" + docUri + ", pathTrail='" + pathTrail + "'");
    LOG.finest(() -> "detectAndThrowCycle: docUri object=" + docUri + ", scheme=" + docUri.getScheme() + ", host=" + docUri.getHost() + ", path=" + docUri.getPath());
    if (active.contains(docUri)) {
      String cycleMessage = "ERROR: " + pathTrail + " -> " + docUri + " (compile-time remote ref cycle)";
      LOG.severe(() -> cycleMessage);
      throw new IllegalArgumentException(cycleMessage);
    }
    LOG.finest(() -> "detectAndThrowCycle: no cycle detected");
  }

  /// Freeze roots into immutable registry
  static CompiledRegistry freezeRoots(Map<java.net.URI, CompiledRoot> built, java.net.URI primaryUri) {
    LOG.fine(() -> "freezeRoots: freezing " + built.size() + " compiled roots");
    LOG.finest(() -> "freezeRoots: built map object=" + built + ", keys=" + built.keySet() + ", values=" + built.values() + ", size=" + built.size());

    // Find entry root by the provided primary URI
    CompiledRoot entryRoot = built.get(primaryUri);
    if (entryRoot == null) {
      // Fallback: if not found, attempt to get by base URI without fragment
      java.net.URI alt = java.net.URI.create(primaryUri.toString());
      entryRoot = built.get(alt);
    }
    if (entryRoot == null) {
      // As a last resort, pick the first element to avoid NPE, but log an error
      LOG.severe(() -> "ERROR: Primary root URI not found in compiled roots: " + primaryUri);
      entryRoot = built.values().iterator().next();
    }
    final java.net.URI primaryResolved = entryRoot.docUri();
    final java.net.URI entryDocUri = entryRoot.docUri();
    final String entrySchemaType = entryRoot.schema().getClass().getSimpleName();
    LOG.finest(() -> "freezeRoots: entryRoot docUri=" + entryDocUri + ", schemaType=" + entrySchemaType);
    LOG.finest(() -> "freezeRoots: primaryUri object=" + primaryResolved + ", scheme=" + primaryResolved.getScheme() + ", host=" + primaryResolved.getHost() + ", path=" + primaryResolved.getPath());

    LOG.fine(() -> "freezeRoots: primary root URI: " + primaryResolved);

    // Create immutable map
    Map<java.net.URI, CompiledRoot> frozenRoots = Map.copyOf(built);
    LOG.finest(() -> "freezeRoots: frozenRoots map object=" + frozenRoots + ", keys=" + frozenRoots.keySet() + ", values=" + frozenRoots.values() + ", size=" + frozenRoots.size());

    CompiledRegistry registry = new CompiledRegistry(frozenRoots, entryRoot);
    LOG.finest(() -> "freezeRoots: created CompiledRegistry object=" + registry + ", entry=" + registry.entry() + ", roots.size=" + registry.roots().size());
    return registry;
  }

  /// Create resolver context from compiled registry
  static ResolverContext createResolverContextFromRegistry(CompiledRegistry registry) {
    LOG.fine(() -> "createResolverContextFromRegistry: creating context from registry with " + registry.roots().size() + " roots");
    LOG.finest(() -> "createResolverContextFromRegistry: registry object=" + registry + ", entry=" + registry.entry() + ", roots.keys=" + registry.roots().keySet());

    Map<java.net.URI, CompiledRoot> updatedRoots = new HashMap<>();

    // Provisional context that references updatedRoots; we fill it next, so RefSchemas will close over this map.
    Map<String, JsonSchema> entryPointerIndex = new HashMap<>(registry.entry().pointerIndex());
    ResolverContext provisional = new ResolverContext(updatedRoots, entryPointerIndex, registry.entry().schema());

    // Reattach context to every compiled root schema tree
    for (var e : registry.roots().entrySet()) {
      java.net.URI uri = e.getKey();
      CompiledRoot root = e.getValue();
      JsonSchema remapped = reattachContext(root.schema(), provisional);
      updatedRoots.put(uri, new CompiledRoot(uri, remapped, root.pointerIndex()));
    }

    // Entry root with reattached schema
    CompiledRoot newEntry = updatedRoots.get(registry.entry().docUri());
    if (newEntry == null) newEntry = registry.entry();

    ResolverContext context = new ResolverContext(updatedRoots, new HashMap<>(newEntry.pointerIndex()), newEntry.schema());
    LOG.fine(() -> "createResolverContextFromRegistry: created context with " + context.roots().size() + " roots");
    LOG.finest(() -> "createResolverContextFromRegistry: context object=" + context + ", roots.size=" + context.roots().size() + ", localPointerIndex.size=" + context.localPointerIndex().size());
    return context;
  }

  /// Update schema tree to use full resolver context
  static JsonSchema updateSchemaWithFullContext(JsonSchema schema, ResolverContext fullContext) {
    LOG.fine(() -> "updateSchemaWithFullContext: updating schema " + schema.getClass().getSimpleName() + " with full context");
    LOG.finest(() -> "updateSchemaWithFullContext: schema object=" + schema + ", fullContext.roots.size=" + fullContext.roots().size());
    return reattachContext(schema, fullContext);
  }

  private static JsonSchema reattachContext(JsonSchema schema, ResolverContext ctx) {
    return switch (schema) {
      case RefSchema ref -> {
        LOG.fine(() -> "reattachContext: RefSchema");
        yield new RefSchema(ref.refToken(), ctx);
      }

      case AllOfSchema all -> {
        LOG.fine(() -> "reattachContext: AllOfSchema");
        LOG.finer(() -> "reattachContext: AllOf count=" + all.schemas().size());
        List<JsonSchema> mapped = new ArrayList<>(all.schemas().size());
        for (JsonSchema s : all.schemas()) mapped.add(reattachContext(s, ctx));
        LOG.finest(() -> "reattachContext: AllOf mapped=" + mapped);
        yield new AllOfSchema(List.copyOf(mapped));
      }

      case AnyOfSchema any -> {
        LOG.fine(() -> "reattachContext: AnyOfSchema");
        LOG.finer(() -> "reattachContext: AnyOf count=" + any.schemas().size());
        List<JsonSchema> mapped = new ArrayList<>(any.schemas().size());
        for (JsonSchema s : any.schemas()) mapped.add(reattachContext(s, ctx));
        LOG.finest(() -> "reattachContext: AnyOf mapped=" + mapped);
        yield new AnyOfSchema(List.copyOf(mapped));
      }

      case OneOfSchema one -> {
        LOG.fine(() -> "reattachContext: OneOfSchema");
        LOG.finer(() -> "reattachContext: OneOf count=" + one.schemas().size());
        List<JsonSchema> mapped = new ArrayList<>(one.schemas().size());
        for (JsonSchema s : one.schemas()) mapped.add(reattachContext(s, ctx));
        LOG.finest(() -> "reattachContext: OneOf mapped=" + mapped);
        yield new OneOfSchema(List.copyOf(mapped));
      }

      case ConditionalSchema cond -> {
        LOG.fine(() -> "reattachContext: ConditionalSchema");
        JsonSchema ifS = reattachContext(cond.ifSchema(), ctx);
        JsonSchema thenS = cond.thenSchema() == null ? null : reattachContext(cond.thenSchema(), ctx);
        JsonSchema elseS = cond.elseSchema() == null ? null : reattachContext(cond.elseSchema(), ctx);
        LOG.finer(() -> "reattachContext: Conditional branches then=" + (thenS != null) + ", else=" + (elseS != null));
        yield new ConditionalSchema(ifS, thenS, elseS);
      }

      case NotSchema not -> {
        LOG.fine(() -> "reattachContext: NotSchema");
        yield new NotSchema(reattachContext(not.schema(), ctx));
      }

      case EnumSchema en -> {
        LOG.fine(() -> "reattachContext: EnumSchema");
        LOG.finer(() -> "reattachContext: Enum allowed count=" + en.allowedValues().size());
        yield new EnumSchema(reattachContext(en.baseSchema(), ctx), en.allowedValues());
      }

      case ObjectSchema obj -> {
        LOG.fine(() -> "reattachContext: ObjectSchema");
        LOG.finer(() -> "reattachContext: properties=" + obj.properties().size()
            + ", dependentSchemas=" + (obj.dependentSchemas() == null ? 0 : obj.dependentSchemas().size())
            + ", patternProperties=" + (obj.patternProperties() == null ? 0 : obj.patternProperties().size()));
        Map<String, JsonSchema> props = new LinkedHashMap<>();
        for (var e : obj.properties().entrySet()) props.put(e.getKey(), reattachContext(e.getValue(), ctx));
        LOG.finest(() -> "reattachContext: property keys=" + props.keySet());
        Map<Pattern, JsonSchema> patternProps = null;
        if (obj.patternProperties() != null) {
          patternProps = new LinkedHashMap<>();
          for (var e : obj.patternProperties().entrySet()) patternProps.put(e.getKey(), reattachContext(e.getValue(), ctx));
        }
        JsonSchema additional = obj.additionalProperties();
        if (additional != null && additional != BooleanSchema.TRUE && additional != BooleanSchema.FALSE) {
          additional = reattachContext(additional, ctx);
        }
        JsonSchema propertyNames = obj.propertyNames();
        if (propertyNames != null) propertyNames = reattachContext(propertyNames, ctx);
        Map<String, JsonSchema> dependSchemas = null;
        if (obj.dependentSchemas() != null) {
          dependSchemas = new LinkedHashMap<>();
          for (var e : obj.dependentSchemas().entrySet()) {
            JsonSchema v = e.getValue();
            if (v != BooleanSchema.TRUE && v != BooleanSchema.FALSE) v = reattachContext(v, ctx);
            dependSchemas.put(e.getKey(), v);
          }
        }
        yield new ObjectSchema(
            Map.copyOf(props),
            obj.required(),
            additional,
            obj.minProperties(),
            obj.maxProperties(),
            patternProps == null ? null : Map.copyOf(patternProps),
            propertyNames,
            obj.dependentRequired(),
            dependSchemas == null ? null : Map.copyOf(dependSchemas)
        );
      }

      case ArraySchema arr -> {
        LOG.fine(() -> "reattachContext: ArraySchema");
        JsonSchema items = arr.items();
        if (items != null) items = reattachContext(items, ctx);
        List<JsonSchema> prefix = null;
        if (arr.prefixItems() != null) {
          prefix = new ArrayList<>(arr.prefixItems().size());
          for (JsonSchema s : arr.prefixItems()) prefix.add(reattachContext(s, ctx));
        }
        JsonSchema contains = arr.contains();
        if (contains != null) contains = reattachContext(contains, ctx);
        yield new ArraySchema(items, arr.minItems(), arr.maxItems(), arr.uniqueItems(),
            prefix == null ? null : List.copyOf(prefix), contains, arr.minContains(), arr.maxContains());
      }

      // Leaf schemas and those without nested refs
      default -> schema;
    };
  }

  /// Validates JSON document against this schema
  ///
  /// @param json JSON value to validate
  /// @return ValidationResult with success/failure information
  default ValidationResult validate(JsonValue json) {
    Objects.requireNonNull(json, "json");
    List<ValidationError> errors = new ArrayList<>();
    Deque<ValidationFrame> stack = new ArrayDeque<>();
    Set<ValidationKey> visited = new HashSet<>();
    stack.push(new ValidationFrame("", this, json));

    int iterationCount = 0;
    final int WARNING_THRESHOLD = 1000; // Warn after 1000 iterations

    while (!stack.isEmpty()) {
      iterationCount++;
      if (iterationCount % WARNING_THRESHOLD == 0) {
        final int count = iterationCount;
        LOG.warning(() -> "PERFORMANCE WARNING: Validation stack processing " + count +
            " iterations - possible infinite recursion or deeply nested schema");
      }

      ValidationFrame frame = stack.pop();
      ValidationKey key = new ValidationKey(frame.schema(), frame.json(), frame.path());
      if (!visited.add(key)) {
        LOG.finest(() -> "SKIP " + frame.path() + "   schema=" + frame.schema().getClass().getSimpleName());
        continue;
      }
      LOG.finest(() -> "POP " + frame.path() +
          "   schema=" + frame.schema().getClass().getSimpleName());
      ValidationResult result = frame.schema.validateAt(frame.path, frame.json, stack);
      if (!result.valid()) {
        errors.addAll(result.errors());
      }
    }

    return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
  }

  /// Internal validation method used by stack-based traversal
  ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack);

  /// Object schema with properties, required fields, and constraints
  record ObjectSchema(
      Map<String, JsonSchema> properties,
      Set<String> required,
      JsonSchema additionalProperties,
      Integer minProperties,
      Integer maxProperties,
      Map<Pattern, JsonSchema> patternProperties,
      JsonSchema propertyNames,
      Map<String, Set<String>> dependentRequired,
      Map<String, JsonSchema> dependentSchemas
  ) implements JsonSchema {

    @Override
    public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
      if (!(json instanceof JsonObject obj)) {
        return ValidationResult.failure(List.of(
            new ValidationError(path, "Expected object")
        ));
      }

      List<ValidationError> errors = new ArrayList<>();

      // Check property count constraints
      int propCount = obj.members().size();
      if (minProperties != null && propCount < minProperties) {
        errors.add(new ValidationError(path, "Too few properties: expected at least " + minProperties));
      }
      if (maxProperties != null && propCount > maxProperties) {
        errors.add(new ValidationError(path, "Too many properties: expected at most " + maxProperties));
      }

      // Check required properties
      for (String reqProp : required) {
        if (!obj.members().containsKey(reqProp)) {
          errors.add(new ValidationError(path, "Missing required property: " + reqProp));
        }
      }

      // Handle dependentRequired
      if (dependentRequired != null) {
        for (var entry : dependentRequired.entrySet()) {
          String triggerProp = entry.getKey();
          Set<String> requiredDeps = entry.getValue();

          // If trigger property is present, check all dependent properties
          if (obj.members().containsKey(triggerProp)) {
            for (String depProp : requiredDeps) {
              if (!obj.members().containsKey(depProp)) {
                errors.add(new ValidationError(path, "Property '" + triggerProp + "' requires property '" + depProp + "' (dependentRequired)"));
              }
            }
          }
        }
      }

      // Handle dependentSchemas
      if (dependentSchemas != null) {
        for (var entry : dependentSchemas.entrySet()) {
          String triggerProp = entry.getKey();
          JsonSchema depSchema = entry.getValue();

          // If trigger property is present, apply the dependent schema
          if (obj.members().containsKey(triggerProp)) {
            if (depSchema == BooleanSchema.FALSE) {
              errors.add(new ValidationError(path, "Property '" + triggerProp + "' forbids object unless its dependent schema is satisfied (dependentSchemas=false)"));
            } else if (depSchema != BooleanSchema.TRUE) {
              // Apply the dependent schema to the entire object
              stack.push(new ValidationFrame(path, depSchema, json));
            }
          }
        }
      }

      // Validate property names if specified
      if (propertyNames != null) {
        for (String propName : obj.members().keySet()) {
          String namePath = path.isEmpty() ? propName : path + "." + propName;
          JsonValue nameValue = Json.parse("\"" + propName + "\"");
          ValidationResult nameResult = propertyNames.validateAt(namePath + "(name)", nameValue, stack);
          if (!nameResult.valid()) {
            errors.add(new ValidationError(namePath, "Property name violates propertyNames"));
          }
        }
      }

      // Validate each property with correct precedence
      for (var entry : obj.members().entrySet()) {
        String propName = entry.getKey();
        JsonValue propValue = entry.getValue();
        String propPath = path.isEmpty() ? propName : path + "." + propName;

        // Track if property was handled by properties or patternProperties
        boolean handledByProperties = false;
        boolean handledByPattern = false;

        // 1. Check if property is in properties (highest precedence)
        JsonSchema propSchema = properties.get(propName);
        if (propSchema != null) {
          stack.push(new ValidationFrame(propPath, propSchema, propValue));
          handledByProperties = true;
        }

        // 2. Check all patternProperties that match this property name
        if (patternProperties != null) {
          for (var patternEntry : patternProperties.entrySet()) {
            Pattern pattern = patternEntry.getKey();
            JsonSchema patternSchema = patternEntry.getValue();
            if (pattern.matcher(propName).find()) { // unanchored find semantics
              stack.push(new ValidationFrame(propPath, patternSchema, propValue));
              handledByPattern = true;
            }
          }
        }

        // 3. If property wasn't handled by properties or patternProperties, apply additionalProperties
        if (!handledByProperties && !handledByPattern) {
          if (additionalProperties != null) {
            if (additionalProperties == BooleanSchema.FALSE) {
              // Handle additionalProperties: false - reject unmatched properties
              errors.add(new ValidationError(propPath, "Additional properties not allowed"));
            } else if (additionalProperties != BooleanSchema.TRUE) {
              // Apply the additionalProperties schema (not true/false boolean schemas)
              stack.push(new ValidationFrame(propPath, additionalProperties, propValue));
            }
          }
        }
      }

      return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }
  }

  /// Array schema with item validation and constraints
  record ArraySchema(
      JsonSchema items,
      Integer minItems,
      Integer maxItems,
      Boolean uniqueItems,
      // NEW: Pack 2 array features
      List<JsonSchema> prefixItems,
      JsonSchema contains,
      Integer minContains,
      Integer maxContains
  ) implements JsonSchema {

    @Override
    public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
      if (!(json instanceof JsonArray arr)) {
        return ValidationResult.failure(List.of(
            new ValidationError(path, "Expected array")
        ));
      }

      List<ValidationError> errors = new ArrayList<>();
      int itemCount = arr.values().size();

      // Check item count constraints
      if (minItems != null && itemCount < minItems) {
        errors.add(new ValidationError(path, "Too few items: expected at least " + minItems));
      }
      if (maxItems != null && itemCount > maxItems) {
        errors.add(new ValidationError(path, "Too many items: expected at most " + maxItems));
      }

      // Check uniqueness if required (structural equality)
      if (uniqueItems != null && uniqueItems) {
        Set<String> seen = new HashSet<>();
        for (JsonValue item : arr.values()) {
          String canonicalKey = canonicalize(item);
          if (!seen.add(canonicalKey)) {
            errors.add(new ValidationError(path, "Array items must be unique"));
            break;
          }
        }
      }

      // Validate prefixItems + items (tuple validation)
      if (prefixItems != null && !prefixItems.isEmpty()) {
        // Validate prefix items - fail if not enough items for all prefix positions
        for (int i = 0; i < prefixItems.size(); i++) {
          if (i >= itemCount) {
            errors.add(new ValidationError(path, "Array has too few items for prefixItems validation"));
            break;
          }
          String itemPath = path + "[" + i + "]";
          // Validate prefix items immediately to capture errors
          ValidationResult prefixResult = prefixItems.get(i).validateAt(itemPath, arr.values().get(i), stack);
          if (!prefixResult.valid()) {
            errors.addAll(prefixResult.errors());
          }
        }
        // Validate remaining items with items schema if present
        if (items != null && items != AnySchema.INSTANCE) {
          for (int i = prefixItems.size(); i < itemCount; i++) {
            String itemPath = path + "[" + i + "]";
            stack.push(new ValidationFrame(itemPath, items, arr.values().get(i)));
          }
        }
      } else if (items != null && items != AnySchema.INSTANCE) {
        // Original items validation (no prefixItems)
        int index = 0;
        for (JsonValue item : arr.values()) {
          String itemPath = path + "[" + index + "]";
          stack.push(new ValidationFrame(itemPath, items, item));
          index++;
        }
      }

      // Validate contains / minContains / maxContains
      if (contains != null) {
        int matchCount = 0;
        for (JsonValue item : arr.values()) {
          // Create isolated validation to check if item matches contains schema
          Deque<ValidationFrame> tempStack = new ArrayDeque<>();
          List<ValidationError> tempErrors = new ArrayList<>();
          tempStack.push(new ValidationFrame("", contains, item));

          while (!tempStack.isEmpty()) {
            ValidationFrame frame = tempStack.pop();
            ValidationResult result = frame.schema().validateAt(frame.path(), frame.json(), tempStack);
            if (!result.valid()) {
              tempErrors.addAll(result.errors());
            }
          }

          if (tempErrors.isEmpty()) {
            matchCount++;
          }
        }

        int min = (minContains != null ? minContains : 1); // default min=1
        int max = (maxContains != null ? maxContains : Integer.MAX_VALUE); // default max=∞

        if (matchCount < min) {
          errors.add(new ValidationError(path, "Array must contain at least " + min + " matching element(s)"));
        } else if (matchCount > max) {
          errors.add(new ValidationError(path, "Array must contain at most " + max + " matching element(s)"));
        }
      }

      return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }
  }

  /// String schema with length, pattern, and enum constraints
  record StringSchema(
      Integer minLength,
      Integer maxLength,
      Pattern pattern,
      FormatValidator formatValidator,
      boolean assertFormats
  ) implements JsonSchema {

    @Override
    public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
      if (!(json instanceof JsonString str)) {
        return ValidationResult.failure(List.of(
            new ValidationError(path, "Expected string")
        ));
      }

      String value = str.value();
      List<ValidationError> errors = new ArrayList<>();

      // Check length constraints
      int length = value.length();
      if (minLength != null && length < minLength) {
        errors.add(new ValidationError(path, "String too short: expected at least " + minLength + " characters"));
      }
      if (maxLength != null && length > maxLength) {
        errors.add(new ValidationError(path, "String too long: expected at most " + maxLength + " characters"));
      }

      // Check pattern (unanchored matching - uses find() instead of matches())
      if (pattern != null && !pattern.matcher(value).find()) {
        errors.add(new ValidationError(path, "Pattern mismatch"));
      }

      // Check format validation (only when format assertion is enabled)
      if (formatValidator != null && assertFormats) {
        if (!formatValidator.test(value)) {
          String formatName = formatValidator instanceof Format format ? format.name().toLowerCase().replace("_", "-") : "unknown";
          errors.add(new ValidationError(path, "Invalid format '" + formatName + "'"));
        }
      }

      return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }
  }

  /// Number schema with range and multiple constraints
  record NumberSchema(
      BigDecimal minimum,
      BigDecimal maximum,
      BigDecimal multipleOf,
      Boolean exclusiveMinimum,
      Boolean exclusiveMaximum
  ) implements JsonSchema {

    @Override
    public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
      LOG.finest(() -> "NumberSchema.validateAt: " + json + " minimum=" + minimum + " maximum=" + maximum);
      if (!(json instanceof JsonNumber num)) {
        return ValidationResult.failure(List.of(
            new ValidationError(path, "Expected number")
        ));
      }

      BigDecimal value = num.toNumber() instanceof BigDecimal bd ? bd : BigDecimal.valueOf(num.toNumber().doubleValue());
      List<ValidationError> errors = new ArrayList<>();

      // Check minimum
      if (minimum != null) {
        int comparison = value.compareTo(minimum);
        LOG.finest(() -> "NumberSchema.validateAt: value=" + value + " minimum=" + minimum + " comparison=" + comparison);
        if (exclusiveMinimum != null && exclusiveMinimum && comparison <= 0) {
          errors.add(new ValidationError(path, "Below minimum"));
        } else if (comparison < 0) {
          errors.add(new ValidationError(path, "Below minimum"));
        }
      }

      // Check maximum
      if (maximum != null) {
        int comparison = value.compareTo(maximum);
        if (exclusiveMaximum != null && exclusiveMaximum && comparison >= 0) {
          errors.add(new ValidationError(path, "Above maximum"));
        } else if (comparison > 0) {
          errors.add(new ValidationError(path, "Above maximum"));
        }
      }

      // Check multipleOf
      if (multipleOf != null) {
        BigDecimal remainder = value.remainder(multipleOf);
        if (remainder.compareTo(BigDecimal.ZERO) != 0) {
          errors.add(new ValidationError(path, "Not multiple of " + multipleOf));
        }
      }

      return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }
  }

  /// Boolean schema - validates boolean values
  record BooleanSchema() implements JsonSchema {
    /// Singleton instances for boolean sub-schema handling
    static final BooleanSchema TRUE = new BooleanSchema();
    static final BooleanSchema FALSE = new BooleanSchema();

    @Override
    public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
      // For boolean subschemas, FALSE always fails, TRUE always passes
      if (this == FALSE) {
        return ValidationResult.failure(List.of(
            new ValidationError(path, "Schema should not match")
        ));
      }
      if (this == TRUE) {
        return ValidationResult.success();
      }
      // Regular boolean validation for normal boolean schemas
      if (!(json instanceof JsonBoolean)) {
        return ValidationResult.failure(List.of(
            new ValidationError(path, "Expected boolean")
        ));
      }
      return ValidationResult.success();
    }
  }

  /// Null schema - always valid for null values
  record NullSchema() implements JsonSchema {
    @Override
    public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
      if (!(json instanceof JsonNull)) {
        return ValidationResult.failure(List.of(
            new ValidationError(path, "Expected null")
        ));
      }
      return ValidationResult.success();
    }
  }

  /// Any schema - accepts all values
  record AnySchema() implements JsonSchema {
    static final AnySchema INSTANCE = new AnySchema();

    @Override
    public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
      return ValidationResult.success();
    }
  }

  /// Reference schema for JSON Schema $ref
  record RefSchema(RefToken refToken, ResolverContext resolverContext) implements JsonSchema {
    @Override
    public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
      LOG.finest(() -> "RefSchema.validateAt: " + refToken + " at path: " + path + " with json=" + json);
      LOG.fine(() -> "RefSchema.validateAt: Using resolver context with roots.size=" + resolverContext.roots().size() +
          " localPointerIndex.size=" + resolverContext.localPointerIndex().size());

      // Add detailed logging for remote ref resolution
      if (refToken instanceof RefToken.RemoteRef(URI baseUri, URI targetUri)) {
        LOG.finest(() -> "RefSchema.validateAt: Attempting to resolve RemoteRef: baseUri=" + baseUri + ", targetUri=" + targetUri);
        LOG.finest(() -> "RefSchema.validateAt: Available roots in context: " + resolverContext.roots().keySet());
      }

      JsonSchema target = resolverContext.resolve(refToken);
      LOG.finest(() -> "RefSchema.validateAt: Resolved target=" + target);
      if (target == null) {
        return ValidationResult.failure(List.of(new ValidationError(path, "Unresolvable $ref: " + refToken)));
      }
      // Stay on the SAME traversal stack (uniform non-recursive execution).
      stack.push(new ValidationFrame(path, target, json));
      return ValidationResult.success();
    }

    @Override
    public String toString() {
      return "RefSchema[" + refToken + "]";
    }
  }

  /// AllOf composition - must satisfy all schemas
  record AllOfSchema(List<JsonSchema> schemas) implements JsonSchema {
    @Override
    public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
      // Push all subschemas onto the stack for validation
      for (JsonSchema schema : schemas) {
        stack.push(new ValidationFrame(path, schema, json));
      }
      return ValidationResult.success(); // Actual results emerge from stack processing
    }
  }

  /// AnyOf composition - must satisfy at least one schema
  record AnyOfSchema(List<JsonSchema> schemas) implements JsonSchema {
    @Override
    public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
      List<ValidationError> collected = new ArrayList<>();
      boolean anyValid = false;

      for (JsonSchema schema : schemas) {
        // Create a separate validation stack for this branch
        Deque<ValidationFrame> branchStack = new ArrayDeque<>();
        List<ValidationError> branchErrors = new ArrayList<>();

        LOG.finest(() -> "BRANCH START: " + schema.getClass().getSimpleName());
        branchStack.push(new ValidationFrame(path, schema, json));

        while (!branchStack.isEmpty()) {
          ValidationFrame frame = branchStack.pop();
          ValidationResult result = frame.schema().validateAt(frame.path(), frame.json(), branchStack);
          if (!result.valid()) {
            branchErrors.addAll(result.errors());
          }
        }

        if (branchErrors.isEmpty()) {
          anyValid = true;
          break;
        }
        collected.addAll(branchErrors);
        LOG.finest(() -> "BRANCH END: " + branchErrors.size() + " errors");
      }

      return anyValid ? ValidationResult.success() : ValidationResult.failure(collected);
    }
  }

  /// OneOf composition - must satisfy exactly one schema
  record OneOfSchema(List<JsonSchema> schemas) implements JsonSchema {
    @Override
    public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
      int validCount = 0;
      List<ValidationError> minimalErrors = null;

      for (JsonSchema schema : schemas) {
        // Create a separate validation stack for this branch
        Deque<ValidationFrame> branchStack = new ArrayDeque<>();
        List<ValidationError> branchErrors = new ArrayList<>();

        LOG.finest(() -> "ONEOF BRANCH START: " + schema.getClass().getSimpleName());
        branchStack.push(new ValidationFrame(path, schema, json));

        while (!branchStack.isEmpty()) {
          ValidationFrame frame = branchStack.pop();
          ValidationResult result = frame.schema().validateAt(frame.path(), frame.json(), branchStack);
          if (!result.valid()) {
            branchErrors.addAll(result.errors());
          }
        }

        if (branchErrors.isEmpty()) {
          validCount++;
        } else {
          // Track minimal error set for zero-valid case
          // Prefer errors that don't start with "Expected" (type mismatches) if possible
          // In case of ties, prefer later branches (they tend to be more specific)
          if (minimalErrors == null ||
              (branchErrors.size() < minimalErrors.size()) ||
              (branchErrors.size() == minimalErrors.size() &&
                  hasBetterErrorType(branchErrors, minimalErrors))) {
            minimalErrors = branchErrors;
          }
        }
        LOG.finest(() -> "ONEOF BRANCH END: " + branchErrors.size() + " errors, valid=" + branchErrors.isEmpty());
      }

      // Exactly one must be valid
      if (validCount == 1) {
        return ValidationResult.success();
      } else if (validCount == 0) {
        // Zero valid - return minimal error set
        return ValidationResult.failure(minimalErrors != null ? minimalErrors : List.of());
      } else {
        // Multiple valid - single error
        return ValidationResult.failure(List.of(
            new ValidationError(path, "oneOf: multiple schemas matched (" + validCount + ")")
        ));
      }
    }

    private boolean hasBetterErrorType(List<ValidationError> newErrors, List<ValidationError> currentErrors) {
      // Prefer errors that don't start with "Expected" (type mismatches)
      boolean newHasTypeMismatch = newErrors.stream().anyMatch(e -> e.message().startsWith("Expected"));
      boolean currentHasTypeMismatch = currentErrors.stream().anyMatch(e -> e.message().startsWith("Expected"));

      // If new has type mismatch and current doesn't, current is better (keep current)
      return !newHasTypeMismatch || currentHasTypeMismatch;

      // If current has type mismatch and new doesn't, new is better (replace current)

      // If both have type mismatches or both don't, prefer later branches
      // This is a simple heuristic
    }
  }

  /// If/Then/Else conditional schema
  record ConditionalSchema(JsonSchema ifSchema, JsonSchema thenSchema, JsonSchema elseSchema) implements JsonSchema {
    @Override
    public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
      // Step 1 - evaluate IF condition (still needs direct validation)
      ValidationResult ifResult = ifSchema.validate(json);

      // Step 2 - choose branch
      JsonSchema branch = ifResult.valid() ? thenSchema : elseSchema;

      LOG.finer(() -> String.format(
          "Conditional path=%s ifValid=%b branch=%s",
          path, ifResult.valid(),
          branch == null ? "none" : (ifResult.valid() ? "then" : "else")));

      // Step 3 - if there's a branch, push it onto the stack for later evaluation
      if (branch == null) {
        return ValidationResult.success();      // no branch → accept
      }

      // NEW: push branch onto SAME stack instead of direct call
      stack.push(new ValidationFrame(path, branch, json));
      return ValidationResult.success();          // real result emerges later
    }
  }

  /// Validation result types
  record ValidationResult(boolean valid, List<ValidationError> errors) {
    public static ValidationResult success() {
      return new ValidationResult(true, List.of());
    }

    public static ValidationResult failure(List<ValidationError> errors) {
      return new ValidationResult(false, errors);
    }
  }

  record ValidationError(String path, String message) {
  }

  /// Validation frame for stack-based processing
  record ValidationFrame(String path, JsonSchema schema, JsonValue json) {
  }

  /// Internal key used to detect and break validation cycles
  final class ValidationKey {
    private final JsonSchema schema;
    private final JsonValue json;
    private final String path;

    ValidationKey(JsonSchema schema, JsonValue json, String path) {
      this.schema = schema;
      this.json = json;
      this.path = path;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof ValidationKey other)) {
        return false;
      }
      return this.schema == other.schema &&
          this.json == other.json &&
          Objects.equals(this.path, other.path);
    }

    @Override
    public int hashCode() {
      int result = System.identityHashCode(schema);
      result = 31 * result + System.identityHashCode(json);
      result = 31 * result + (path != null ? path.hashCode() : 0);
      return result;
    }
  }

  /// Canonicalization helper for structural equality in uniqueItems
  private static String canonicalize(JsonValue v) {
    switch (v) {
      case JsonObject o -> {
        var keys = new ArrayList<>(o.members().keySet());
        Collections.sort(keys);
        var sb = new StringBuilder("{");
        for (int i = 0; i < keys.size(); i++) {
          String k = keys.get(i);
          if (i > 0) sb.append(',');
          sb.append('"').append(escapeJsonString(k)).append("\":").append(canonicalize(o.members().get(k)));
        }
        return sb.append('}').toString();
      }
      case JsonArray a -> {
        var sb = new StringBuilder("[");
        for (int i = 0; i < a.values().size(); i++) {
          if (i > 0) sb.append(',');
          sb.append(canonicalize(a.values().get(i)));
        }
        return sb.append(']').toString();
      }
      case JsonString s -> {
        return "\"" + escapeJsonString(s.value()) + "\"";
      }
      case null, default -> {
        // numbers/booleans/null: rely on stable toString from the Json* impls
        assert v != null;
        return v.toString();
      }
    }
  }

  private static String escapeJsonString(String s) {
    if (s == null) return "null";
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char ch = s.charAt(i);
      switch (ch) {
        case '"':
          result.append("\\\"");
          break;
        case '\\':
          result.append("\\\\");
          break;
        case '\b':
          result.append("\\b");
          break;
        case '\f':
          result.append("\\f");
          break;
        case '\n':
          result.append("\\n");
          break;
        case '\r':
          result.append("\\r");
          break;
        case '\t':
          result.append("\\t");
          break;
        default:
          if (ch < 0x20 || ch > 0x7e) {
            result.append("\\u").append(String.format("%04x", (int) ch));
          } else {
            result.append(ch);
          }
      }
    }
    return result.toString();
  }

  /// Internal schema compiler
  final class SchemaCompiler {
    /** Strip any fragment from a URI, returning the base document URI. */
    private static java.net.URI stripFragment(java.net.URI uri) {
      String s = uri.toString();
      int i = s.indexOf('#');
      return i >= 0 ? java.net.URI.create(s.substring(0, i)) : uri;
    }
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private static final Map<String, JsonSchema> definitions = new HashMap<>();
    private static JsonSchema currentRootSchema;
    private static Options currentOptions;
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private static final Map<String, JsonSchema> compiledByPointer = new HashMap<>();
    private static final Map<String, JsonValue> rawByPointer = new HashMap<>();
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private static final Deque<String> resolutionStack = new ArrayDeque<>();

    private static void trace(String stage, JsonValue fragment) {
      if (LOG.isLoggable(Level.FINER)) {
        LOG.finer(() ->
            String.format("[%s] %s", stage, fragment.toString()));
      }
    }

    /// JSON Pointer utility for RFC-6901 fragment navigation
    static Optional<JsonValue> navigatePointer(JsonValue root, String pointer) {
      LOG.fine(() -> "Navigating pointer: '" + pointer + "' from root: " + root);

      if (pointer.isEmpty() || pointer.equals(SCHEMA_POINTER_ROOT)) {
        return Optional.of(root);
      }

      // Remove leading # if present
      String path = pointer.startsWith(SCHEMA_POINTER_ROOT) ? pointer.substring(1) : pointer;
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

      final var currentFinal = current;
      LOG.fine(() -> "Found target: " + currentFinal);
      return Optional.of(current);
    }

    /// Classify a $ref string as local or remote
    static RefToken classifyRef(String ref, java.net.URI baseUri) {
      LOG.fine(() -> "Classifying ref: '" + ref + "' with base URI: " + baseUri);

      if (ref == null || ref.isEmpty()) {
        throw new IllegalArgumentException("InvalidPointer: empty $ref");
      }

      // Check if it's a URI with scheme (remote) or just fragment/local pointer
      try {
        java.net.URI refUri = java.net.URI.create(ref);

        // If it has a scheme or authority, it's remote
        if (refUri.getScheme() != null || refUri.getAuthority() != null) {
          java.net.URI resolvedUri = baseUri.resolve(refUri);
          LOG.finer(() -> "Classified as remote ref: " + resolvedUri);
          return new RefToken.RemoteRef(baseUri, resolvedUri);
        }

        // If it's just a fragment or starts with #, it's local
        if (ref.startsWith(SCHEMA_POINTER_ROOT) || !ref.contains("://")) {
          LOG.finer(() -> "Classified as local ref: " + ref);
          return new RefToken.LocalRef(ref);
        }

        // Default to local for safety during this refactor
        LOG.finer(() -> "Defaulting to local ref: " + ref);
        return new RefToken.LocalRef(ref);
      } catch (IllegalArgumentException e) {
        // Invalid URI syntax - treat as local pointer with error handling
        if (ref.startsWith(SCHEMA_POINTER_ROOT) || ref.startsWith("/")) {
          LOG.finer(() -> "Invalid URI but treating as local ref: " + ref);
          return new RefToken.LocalRef(ref);
        }
        throw new IllegalArgumentException("InvalidPointer: " + ref);
      }
    }

    /// Index schema fragments by JSON Pointer for efficient lookup
    static void indexSchemaByPointer(String pointer, JsonValue value) {
      rawByPointer.put(pointer, value);

      if (value instanceof JsonObject obj) {
        for (var entry : obj.members().entrySet()) {
          String key = entry.getKey();
          // Escape special characters in key
          String escapedKey = key.replace("~", "~0").replace("/", "~1");
          indexSchemaByPointer(pointer + "/" + escapedKey, entry.getValue());
        }
      } else if (value instanceof JsonArray arr) {
        for (int i = 0; i < arr.values().size(); i++) {
          indexSchemaByPointer(pointer + "/" + i, arr.values().get(i));
        }
      }
    }

    /// New stack-driven compilation method that creates CompilationBundle
    static CompilationBundle compileBundle(JsonValue schemaJson, Options options, CompileOptions compileOptions) {
      LOG.fine(() -> "compileBundle: Starting with remote compilation enabled");
      LOG.finest(() -> "compileBundle: Starting with schema: " + schemaJson);

      // Work stack for documents to compile
      Deque<WorkItem> workStack = new ArrayDeque<>();
      Set<java.net.URI> seenUris = new HashSet<>();
      Map<java.net.URI, CompiledRoot> compiled = new HashMap<>();

      // Start with synthetic URI for in-memory root
      java.net.URI entryUri = java.net.URI.create("urn:inmemory:root");
      LOG.finest(() -> "compileBundle: Entry URI: " + entryUri);
      workStack.push(new WorkItem(entryUri));
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

        WorkItem workItem = workStack.pop();
        java.net.URI currentUri = workItem.docUri();
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
          java.net.URI docUri = fragment != null ?
              java.net.URI.create(currentUri.toString().substring(0, currentUri.toString().indexOf('#'))) :
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

          try {
            java.net.URI first = docUri;
            if ("file".equalsIgnoreCase(scheme)) {
              String base = System.getProperty("json.schema.test.resources", "src/test/resources");
              String path = docUri.getPath();
              if (path.startsWith("/")) path = path.substring(1);
              java.nio.file.Path abs = java.nio.file.Paths.get(base, path).toAbsolutePath();
              java.net.URI alt = abs.toUri();
              first = alt;
              LOG.fine(() -> "compileBundle: Using file mapping for fetch: " + alt + " (original=" + docUri + ")");
            }

            RemoteFetcher.FetchResult fetchResult;
            try {
              fetchResult = compileOptions.remoteFetcher().fetch(first, compileOptions.fetchPolicy());
            } catch (RemoteResolutionException e1) {
              if (!first.equals(docUri)) {
                fetchResult = compileOptions.remoteFetcher().fetch(docUri, compileOptions.fetchPolicy());
              } else {
                throw e1;
              }
            }

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

            documentToCompile = fetchResult.document();
            final String normType = documentToCompile.getClass().getSimpleName();
            final java.net.URI normUri = first;
            LOG.fine(() -> "compileBundle: Successfully fetched document (normalized): " + normUri + ", document type: " + normType);
          } catch (RemoteResolutionException e) {
            LOG.severe(() -> "ERROR: compileBundle failed to fetch remote document: " + docUri + ", reason: " + e.reason());
            throw e;
          }
        }

        // Compile the schema
        LOG.finest(() -> "compileBundle: Compiling document for URI: " + currentUri);
        CompilationResult result = compileSingleDocument(documentToCompile, options, compileOptions, currentUri, workStack, seenUris);
        LOG.finest(() -> "compileBundle: Document compilation completed for URI: " + currentUri + ", schema type: " + result.schema().getClass().getSimpleName());

        // Create compiled root and add to map
        CompiledRoot compiledRoot = new CompiledRoot(currentUri, result.schema(), result.pointerIndex());
        compiled.put(currentUri, compiledRoot);
        LOG.fine(() -> "compileBundle: Added compiled root for URI: " + currentUri +
            " with " + result.pointerIndex().size() + " pointer index entries");
      }

      // Create compilation bundle
      CompiledRoot entryRoot = compiled.get(entryUri);
      if (entryRoot == null) {
        LOG.severe(() -> "ERROR: Entry root must exist but was null for URI: " + entryUri);
      }
      assert entryRoot != null : "Entry root must exist";
      List<CompiledRoot> allRoots = List.copyOf(compiled.values());

      LOG.fine(() -> "compileBundle: Creating compilation bundle with " + allRoots.size() + " total compiled roots");

      // Create a map of compiled roots for resolver context
      Map<java.net.URI, CompiledRoot> rootsMap = new HashMap<>();
      LOG.finest(() -> "compileBundle: Creating rootsMap from " + allRoots.size() + " compiled roots");
      for (CompiledRoot root : allRoots) {
        LOG.finest(() -> "compileBundle: Adding root to map: " + root.docUri());
        // Add both with and without fragment for lookup flexibility
        rootsMap.put(root.docUri(), root);
        // Also add the base URI without fragment if it has one
        if (root.docUri().getFragment() != null) {
          java.net.URI baseUri = java.net.URI.create(root.docUri().toString().substring(0, root.docUri().toString().indexOf('#')));
          rootsMap.put(baseUri, root);
          LOG.finest(() -> "compileBundle: Also adding base URI: " + baseUri);
        }
      }
      LOG.finest(() -> "compileBundle: Final rootsMap keys: " + rootsMap.keySet());

      // Create compilation bundle with compiled roots
      List<CompiledRoot> updatedRoots = List.copyOf(compiled.values());
      CompiledRoot updatedEntryRoot = compiled.get(entryUri);

      LOG.fine(() -> "compileBundle: Successfully created compilation bundle with " + updatedRoots.size() +
          " total documents compiled, entry root type: " + updatedEntryRoot.schema().getClass().getSimpleName());
      LOG.finest(() -> "compileBundle: Completed with entry root: " + updatedEntryRoot);
      return new CompilationBundle(updatedEntryRoot, updatedRoots);
    }

    /// Compile a single document using new architecture
    static CompilationResult compileSingleDocument(JsonValue schemaJson, Options options, CompileOptions compileOptions,
                                                   java.net.URI docUri, Deque<WorkItem> workStack, Set<java.net.URI> seenUris) {
      LOG.fine(() -> "compileSingleDocument: Starting compilation for docUri: " + docUri + ", schema type: " + schemaJson.getClass().getSimpleName());

      // Reset global state
      definitions.clear();
      compiledByPointer.clear();
      rawByPointer.clear();
      resolutionStack.clear();
      currentRootSchema = null;
      currentOptions = options;

      LOG.finest(() -> "compileSingleDocument: Reset global state, definitions cleared, pointer indexes cleared");

      // Handle format assertion controls
      boolean assertFormats = options.assertFormats();

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

      // Update options with final assertion setting
      currentOptions = new Options(assertFormats);
      final boolean finalAssertFormats = assertFormats;
      LOG.finest(() -> "compileSingleDocument: Final format assertion setting: " + finalAssertFormats);

      // Index the raw schema by JSON Pointer
      LOG.finest(() -> "compileSingleDocument: Indexing schema by pointer");
      indexSchemaByPointer("", schemaJson);

      // Build local pointer index for this document
      Map<String, JsonSchema> localPointerIndex = new HashMap<>();

      trace("compile-start", schemaJson);
      LOG.finer(() -> "compileSingleDocument: Calling compileInternalWithContext for docUri: " + docUri);
      JsonSchema schema = compileInternalWithContext(schemaJson, docUri, workStack, seenUris, localPointerIndex);
      LOG.finer(() -> "compileSingleDocument: compileInternalWithContext completed, schema type: " + schema.getClass().getSimpleName());

      currentRootSchema = schema; // Store the root schema for self-references
      LOG.fine(() -> "compileSingleDocument: Completed compilation for docUri: " + docUri +
          ", schema type: " + schema.getClass().getSimpleName() + ", local pointer index size: " + localPointerIndex.size());
  return new CompilationResult(schema, Map.copyOf(localPointerIndex));
    }

    private static JsonSchema compileInternalWithContext(JsonValue schemaJson, java.net.URI docUri,
                                                        Deque<WorkItem> workStack, Set<java.net.URI> seenUris,
                                                        Map<String, JsonSchema> localPointerIndex) {
      return compileInternalWithContext(schemaJson, docUri, workStack, seenUris,
          null, localPointerIndex, new ArrayDeque<>());
    }

    private static JsonSchema compileInternalWithContext(JsonValue schemaJson, java.net.URI docUri,
                                                        Deque<WorkItem> workStack, Set<java.net.URI> seenUris,
                                                        ResolverContext resolverContext,
                                                        Map<String, JsonSchema> localPointerIndex,
                                                        Deque<String> resolutionStack,
                                                        String basePointer) {
      LOG.fine(() -> "compileInternalWithContext: Starting with schema: " + schemaJson + ", docUri: " + docUri);

      // Check for $ref at this level first
      if (schemaJson instanceof JsonObject obj) {
        JsonValue refValue = obj.members().get("$ref");
        if (refValue instanceof JsonString refStr) {
          LOG.fine(() -> "compileInternalWithContext: Found $ref: " + refStr.value());
          RefToken refToken = classifyRef(refStr.value(), docUri);

          // Handle remote refs by adding to work stack
          if (refToken instanceof RefToken.RemoteRef remoteRef) {
            LOG.finer(() -> "Remote ref detected: " + remoteRef.targetUri());
            // Get document URI without fragment
            java.net.URI targetDocUri = stripFragment(remoteRef.targetUri());
            if (!seenUris.contains(targetDocUri)) {
              workStack.push(new WorkItem(targetDocUri));
              seenUris.add(targetDocUri);
              LOG.finer(() -> "Added to work stack: " + targetDocUri);
            }
            LOG.finest(() -> "compileInternalWithContext: Creating RefSchema for remote ref " + remoteRef.targetUri());

            // Create temporary resolver context with current document's pointer index
            // The roots map will be populated later when the compilation bundle is created
            Map<java.net.URI, CompiledRoot> tempRoots = new HashMap<>();
            tempRoots.put(docUri, new CompiledRoot(docUri, AnySchema.INSTANCE, localPointerIndex));

            LOG.fine(() -> "Creating temporary RefSchema for remote ref " + remoteRef.targetUri() +
                " with " + localPointerIndex.size() + " local pointer entries");

            var refSchema = new RefSchema(refToken, new ResolverContext(tempRoots, localPointerIndex, AnySchema.INSTANCE));
            LOG.finest(() -> "compileInternalWithContext: Created RefSchema " + refSchema);
            return refSchema;
          }

          // Handle local refs - check if they exist first and detect cycles
          LOG.finer(() -> "Local ref detected, creating RefSchema: " + refToken.pointer());

          String pointer = refToken.pointer();

          // For compilation-time validation, check if the reference exists
          if (!pointer.equals(SCHEMA_POINTER_ROOT) && !pointer.isEmpty() && !localPointerIndex.containsKey(pointer)) {
            // Check if it might be resolvable via JSON Pointer navigation
            Optional<JsonValue> target = navigatePointer(rawByPointer.get(""), pointer);
            if (target.isEmpty() && basePointer != null && !basePointer.isEmpty() && pointer.startsWith(SCHEMA_POINTER_PREFIX)) {
              String combined = basePointer + pointer.substring(1);
              target = navigatePointer(rawByPointer.get(""), combined);
            }
            if (target.isEmpty() && !pointer.startsWith(SCHEMA_DEFS_POINTER)) {
              throw new IllegalArgumentException("Unresolved $ref: " + pointer);
            }
          }

          // Check for cycles and resolve immediately for $defs references
          if (pointer.startsWith(SCHEMA_DEFS_POINTER)) {
            // This is a definition reference - check for cycles and resolve immediately
            if (resolutionStack.contains(pointer)) {
              throw new IllegalArgumentException("Cyclic $ref: " + String.join(" -> ", resolutionStack) + " -> " + pointer);
            }

            // Try to get from local pointer index first (for already compiled definitions)
            JsonSchema cached = localPointerIndex.get(pointer);
            if (cached != null) {
              return cached;
            }

            // Otherwise, resolve via JSON Pointer and compile
            Optional<JsonValue> target = navigatePointer(rawByPointer.get(""), pointer);
            if (target.isEmpty() && pointer.startsWith(SCHEMA_DEFS_POINTER)) {
              // Heuristic fallback: locate the same named definition under any nested $defs
              String defName = pointer.substring(SCHEMA_DEFS_POINTER.length());
              JsonValue rootRaw = rawByPointer.get("");
              // Perform a shallow search over indexed pointers for a matching suffix
              for (var entry2 : rawByPointer.entrySet()) {
                String k = entry2.getKey();
                if (k.endsWith(SCHEMA_DEFS_SEGMENT + defName)) {
                  target = Optional.ofNullable(entry2.getValue());
                  break;
                }
              }
            }
            if (target.isEmpty() && basePointer != null && !basePointer.isEmpty() && pointer.startsWith(SCHEMA_POINTER_PREFIX)) {
              String combined = basePointer + pointer.substring(1);
              target = navigatePointer(rawByPointer.get(""), combined);
            }
            if (target.isPresent()) {
              // Check if the target itself contains a $ref that would create a cycle
              JsonValue targetValue = target.get();
              if (targetValue instanceof JsonObject targetObj) {
                JsonValue targetRef = targetObj.members().get("$ref");
                if (targetRef instanceof JsonString targetRefStr) {
                  String targetRefPointer = targetRefStr.value();
                  if (resolutionStack.contains(targetRefPointer)) {
                    throw new IllegalArgumentException("Cyclic $ref: " + String.join(" -> ", resolutionStack) + " -> " + pointer + " -> " + targetRefPointer);
                  }
                }
              }

              // Push to resolution stack for cycle detection before compiling
              resolutionStack.push(pointer);
              try {
                JsonSchema compiled = compileInternalWithContext(targetValue, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, basePointer);
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
          if (pointer.equals(SCHEMA_POINTER_ROOT) || pointer.isEmpty()) {
            // For root reference, create RootRef that will resolve through ResolverContext
            // The ResolverContext will be updated later with the proper root schema
            return new RootRef(() -> {
              // If we have a resolver context, use it; otherwise fall back to current root
              if (resolverContext != null) {
                return resolverContext.rootSchema();
              }
              return currentRootSchema != null ? currentRootSchema : AnySchema.INSTANCE;
            });
          }

          // Create temporary resolver context with current document's pointer index
          Map<java.net.URI, CompiledRoot> tempRoots = new HashMap<>();
          tempRoots.put(docUri, new CompiledRoot(docUri, AnySchema.INSTANCE, localPointerIndex));

          LOG.fine(() -> "Creating temporary RefSchema for local ref " + refToken.pointer() +
              " with " + localPointerIndex.size() + " local pointer entries");

          // For other references, use RefSchema with deferred resolution
          // Use a temporary resolver context that will be updated later
          return new RefSchema(refToken, new ResolverContext(tempRoots, localPointerIndex, AnySchema.INSTANCE));
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
          String pointer = (basePointer == null || basePointer.isEmpty()) ? SCHEMA_DEFS_POINTER + entry.getKey() : basePointer + "/$defs/" + entry.getKey();
          JsonSchema compiled = compileInternalWithContext(entry.getValue(), docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, pointer);
          definitions.put(pointer, compiled);
          compiledByPointer.put(pointer, compiled);
          localPointerIndex.put(pointer, compiled);

          // Also index by $anchor if present
          if (entry.getValue() instanceof JsonObject defObj) {
            JsonValue anchorValue = defObj.members().get("$anchor");
            if (anchorValue instanceof JsonString anchorStr) {
              String anchorPointer = SCHEMA_POINTER_ROOT + anchorStr.value();
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
          schemas.add(compileInternalWithContext(item, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, basePointer));
        }
        return new AllOfSchema(schemas);
      }

      JsonValue anyOfValue = obj.members().get("anyOf");
      if (anyOfValue instanceof JsonArray anyOfArr) {
        trace("compile-anyof", anyOfValue);
        List<JsonSchema> schemas = new ArrayList<>();
        for (JsonValue item : anyOfArr.values()) {
          schemas.add(compileInternalWithContext(item, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, basePointer));
        }
        return new AnyOfSchema(schemas);
      }

      JsonValue oneOfValue = obj.members().get("oneOf");
      if (oneOfValue instanceof JsonArray oneOfArr) {
        trace("compile-oneof", oneOfValue);
        List<JsonSchema> schemas = new ArrayList<>();
        for (JsonValue item : oneOfArr.values()) {
          schemas.add(compileInternalWithContext(item, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, basePointer));
        }
        return new OneOfSchema(schemas);
      }

      // Handle if/then/else
      JsonValue ifValue = obj.members().get("if");
      if (ifValue != null) {
        trace("compile-conditional", obj);
        JsonSchema ifSchema = compileInternalWithContext(ifValue, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, basePointer);
        JsonSchema thenSchema = null;
        JsonSchema elseSchema = null;

        JsonValue thenValue = obj.members().get("then");
        if (thenValue != null) {
          thenSchema = compileInternalWithContext(thenValue, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, basePointer);
        }

        JsonValue elseValue = obj.members().get("else");
        if (elseValue != null) {
          elseSchema = compileInternalWithContext(elseValue, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, basePointer);
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
        JsonSchema inner = compileInternalWithContext(notValue, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack);
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
                compileObjectSchemaWithContext(obj, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack);
            case "array" ->
                compileArraySchemaWithContext(obj, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack);
            case "string" -> compileStringSchemaWithContext(obj);
            case "number", "integer" -> compileNumberSchemaWithContext(obj);
            case "boolean" -> new BooleanSchema();
            case "null" -> new NullSchema();
            default -> AnySchema.INSTANCE;
          };
        } else if (hasObjectKeywords) {
          baseSchema = compileObjectSchemaWithContext(obj, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack);
        } else if (hasArrayKeywords) {
          baseSchema = compileArraySchemaWithContext(obj, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack);
        } else if (hasStringKeywords) {
          baseSchema = compileStringSchemaWithContext(obj);
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
              compileObjectSchemaWithContext(obj, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack);
          case "array" ->
              compileArraySchemaWithContext(obj, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack);
          case "string" -> compileStringSchemaWithContext(obj);
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
                  compileObjectSchemaWithContext(obj, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack);
              case "array" ->
                  compileArraySchemaWithContext(obj, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack);
              case "string" -> compileStringSchemaWithContext(obj);
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
          return compileObjectSchemaWithContext(obj, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack);
        } else if (hasArrayKeywords) {
          return compileArraySchemaWithContext(obj, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack);
        } else if (hasStringKeywords) {
          return compileStringSchemaWithContext(obj);
        }
      }

      return AnySchema.INSTANCE;
    }

    // Overload: preserve existing call sites with explicit resolverContext and resolutionStack
    private static JsonSchema compileInternalWithContext(JsonValue schemaJson, java.net.URI docUri,
                                                        Deque<WorkItem> workStack, Set<java.net.URI> seenUris,
                                                        ResolverContext resolverContext,
                                                        Map<String, JsonSchema> localPointerIndex,
                                                        Deque<String> resolutionStack) {
      return compileInternalWithContext(schemaJson, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack, SCHEMA_POINTER_ROOT);
    }

    /// Object schema compilation with context
    private static JsonSchema compileObjectSchemaWithContext(JsonObject obj, java.net.URI docUri, Deque<WorkItem> workStack, Set<java.net.URI> seenUris, ResolverContext resolverContext, Map<String, JsonSchema> localPointerIndex, Deque<String> resolutionStack) {
      LOG.finest(() -> "compileObjectSchemaWithContext: Starting with object: " + obj);
      Map<String, JsonSchema> properties = new LinkedHashMap<>();
      JsonValue propsValue = obj.members().get("properties");
      if (propsValue instanceof JsonObject propsObj) {
        LOG.finest(() -> "compileObjectSchemaWithContext: Processing properties: " + propsObj);
        for (var entry : propsObj.members().entrySet()) {
          LOG.finest(() -> "compileObjectSchemaWithContext: Compiling property '" + entry.getKey() + "': " + entry.getValue());
          JsonSchema propertySchema = compileInternalWithContext(entry.getValue(), docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack);
          LOG.finest(() -> "compileObjectSchemaWithContext: Property '" + entry.getKey() + "' compiled to: " + propertySchema);
          properties.put(entry.getKey(), propertySchema);

          // Add to pointer index
          String pointer = SCHEMA_POINTER_ROOT + SCHEMA_PROPERTIES_SEGMENT + entry.getKey();
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
        additionalProperties = compileInternalWithContext(addPropsObj, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack);
      }

      // Handle patternProperties
      Map<Pattern, JsonSchema> patternProperties = null;
      JsonValue patternPropsValue = obj.members().get("patternProperties");
      if (patternPropsValue instanceof JsonObject patternPropsObj) {
        patternProperties = new LinkedHashMap<>();
        for (var entry : patternPropsObj.members().entrySet()) {
          String patternStr = entry.getKey();
          Pattern pattern = Pattern.compile(patternStr);
          JsonSchema schema = compileInternalWithContext(entry.getValue(), docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack);
          patternProperties.put(pattern, schema);
        }
      }

      // Handle propertyNames
      JsonSchema propertyNames = null;
      JsonValue propNamesValue = obj.members().get("propertyNames");
      if (propNamesValue != null) {
        propertyNames = compileInternalWithContext(propNamesValue, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack);
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
            schema = compileInternalWithContext(schemaValue, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack);
          }
          dependentSchemas.put(triggerProp, schema);
        }
      }

      return new ObjectSchema(properties, required, additionalProperties, minProperties, maxProperties, patternProperties, propertyNames, dependentRequired, dependentSchemas);
    }

    /// Array schema compilation with context
    private static JsonSchema compileArraySchemaWithContext(JsonObject obj, java.net.URI docUri, Deque<WorkItem> workStack, Set<java.net.URI> seenUris, ResolverContext resolverContext, Map<String, JsonSchema> localPointerIndex, Deque<String> resolutionStack) {
      JsonSchema items = AnySchema.INSTANCE;
      JsonValue itemsValue = obj.members().get("items");
      if (itemsValue != null) {
        items = compileInternalWithContext(itemsValue, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack);
      }

      // Parse prefixItems (tuple validation)
      List<JsonSchema> prefixItems = null;
      JsonValue prefixItemsVal = obj.members().get("prefixItems");
      if (prefixItemsVal instanceof JsonArray arr) {
        prefixItems = new ArrayList<>(arr.values().size());
        for (JsonValue v : arr.values()) {
          prefixItems.add(compileInternalWithContext(v, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack));
        }
        prefixItems = List.copyOf(prefixItems);
      }

      // Parse contains schema
      JsonSchema contains = null;
      JsonValue containsVal = obj.members().get("contains");
      if (containsVal != null) {
        contains = compileInternalWithContext(containsVal, docUri, workStack, seenUris, resolverContext, localPointerIndex, resolutionStack);
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
    private static JsonSchema compileStringSchemaWithContext(JsonObject obj) {
      Integer minLength = getInteger(obj, "minLength");
      Integer maxLength = getInteger(obj, "maxLength");

      Pattern pattern = null;
      JsonValue patternValue = obj.members().get("pattern");
      if (patternValue instanceof JsonString patternStr) {
        pattern = Pattern.compile(patternStr.value());
      }

      // Handle format keyword
      FormatValidator formatValidator = null;
      boolean assertFormats = currentOptions != null && currentOptions.assertFormats();

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

  /// Const schema - validates that a value equals a constant
  record ConstSchema(JsonValue constValue) implements JsonSchema {
    @Override
    public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
      return json.equals(constValue) ?
          ValidationResult.success() :
          ValidationResult.failure(List.of(new ValidationError(path, "Value must equal const value")));
    }
  }

  /// Enum schema - validates that a value is in a set of allowed values
  record EnumSchema(JsonSchema baseSchema, Set<JsonValue> allowedValues) implements JsonSchema {
    @Override
    public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
      // First validate against base schema
      ValidationResult baseResult = baseSchema.validateAt(path, json, stack);
      if (!baseResult.valid()) {
        return baseResult;
      }

      // Then check if value is in enum
      if (!allowedValues.contains(json)) {
        return ValidationResult.failure(List.of(new ValidationError(path, "Not in enum")));
      }

      return ValidationResult.success();
    }
  }

  /// Not composition - inverts the validation result of the inner schema
  record NotSchema(JsonSchema schema) implements JsonSchema {
    @Override
    public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
      ValidationResult result = schema.validate(json);
      return result.valid() ?
          ValidationResult.failure(List.of(new ValidationError(path, "Schema should not match"))) :
          ValidationResult.success();
    }
  }

  /// Root reference schema that refers back to the root schema
  record RootRef(java.util.function.Supplier<JsonSchema> rootSupplier) implements JsonSchema {
    @Override
    public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
      LOG.finest(() -> "RootRef.validateAt at path: " + path);
      JsonSchema root = rootSupplier.get();
      if (root == null) {
        // Shouldn't happen once compilation finishes; be conservative and fail closed:
        return ValidationResult.failure(List.of(new ValidationError(path, "Root schema not available")));
      }
      // Stay within the SAME stack to preserve traversal semantics (matches AllOf/Conditional).
      stack.push(new ValidationFrame(path, root, json));
      return ValidationResult.success();
    }
  }

  /// Compiled registry holding multiple schema roots
  record CompiledRegistry(
      java.util.Map<java.net.URI, CompiledRoot> roots,
      CompiledRoot entry
  ) {
  }

  /// Classification of a $ref discovered during compilation


  /// Compilation result for a single document
  record CompilationResult(JsonSchema schema, java.util.Map<String, JsonSchema> pointerIndex) {
  }

  /// Immutable compiled document
  record CompiledRoot(java.net.URI docUri, JsonSchema schema, java.util.Map<String, JsonSchema> pointerIndex) {
  }

  /// Work item to load/compile a document
  record WorkItem(java.net.URI docUri) {
  }

  /// Compilation output bundle
  record CompilationBundle(
      CompiledRoot entry,               // the first/root doc
      java.util.List<CompiledRoot> all  // entry + any remotes (for now it'll just be [entry])
  ) {
  }

  /// Resolver context for validation-time $ref resolution
  record ResolverContext(
      java.util.Map<java.net.URI, CompiledRoot> roots,
      java.util.Map<String, JsonSchema> localPointerIndex, // for *entry* root only (for now)
      JsonSchema rootSchema
  ) {
    /// Resolve a RefToken to the target schema
    JsonSchema resolve(RefToken token) {
      LOG.finest(() -> "ResolverContext.resolve: " + token);
      LOG.fine(() -> "ResolverContext.resolve: roots.size=" + roots.size() + ", localPointerIndex.size=" + localPointerIndex.size());

      if (token instanceof RefToken.LocalRef(String pointerOrAnchor)) {

        // Handle root reference
        if (pointerOrAnchor.equals(SCHEMA_POINTER_ROOT) || pointerOrAnchor.isEmpty()) {
          return rootSchema;
        }

        JsonSchema target = localPointerIndex.get(pointerOrAnchor);
        if (target == null) {
          throw new IllegalArgumentException("Unresolved $ref: " + pointerOrAnchor);
        }
        return target;
      }

      if (token instanceof RefToken.RemoteRef remoteRef) {
        LOG.finer(() -> "ResolverContext.resolve: RemoteRef " + remoteRef.targetUri());

        // Get the document URI without fragment
        java.net.URI targetUri = remoteRef.targetUri();
        String originalFragment = targetUri.getFragment();
        java.net.URI docUri = originalFragment != null ?
            java.net.URI.create(targetUri.toString().substring(0, targetUri.toString().indexOf('#'))) :
            targetUri;

        // JSON Pointer fragments should start with #, so add it if missing
        final String fragment;
        if (originalFragment != null && !originalFragment.isEmpty() && !originalFragment.startsWith(SCHEMA_POINTER_PREFIX)) {
          fragment = SCHEMA_POINTER_ROOT + originalFragment;
        } else {
          fragment = originalFragment;
        }

        LOG.finest(() -> "ResolverContext.resolve: docUri=" + docUri + ", fragment=" + fragment);

        // Check if document is already compiled in roots
        final java.net.URI finalDocUri = docUri;
        LOG.fine(() -> "ResolverContext.resolve: Looking for root with URI: " + finalDocUri);
        LOG.fine(() -> "ResolverContext.resolve: Available roots: " + roots.keySet() + " (size=" + roots.size() + ")");
        LOG.fine(() -> "ResolverContext.resolve: This resolver context belongs to root schema: " + rootSchema.getClass().getSimpleName());
        CompiledRoot root = roots.get(finalDocUri);
        if (root == null) {
          // Try without fragment if not found
          final java.net.URI docUriWithoutFragment = finalDocUri.getFragment() != null ?
              java.net.URI.create(finalDocUri.toString().substring(0, finalDocUri.toString().indexOf('#'))) : finalDocUri;
          LOG.fine(() -> "ResolverContext.resolve: Trying without fragment: " + docUriWithoutFragment);
          root = roots.get(docUriWithoutFragment);
        }
        final CompiledRoot finalRoot = root;
        LOG.finest(() -> "ResolverContext.resolve: Found root: " + finalRoot);
        if (finalRoot != null) {
          LOG.finest(() -> "ResolverContext.resolve: Found compiled root for " + docUri);
          // Document already compiled - resolve within it
          if (fragment == null || fragment.isEmpty()) {
            LOG.finest(() -> "ResolverContext.resolve: Returning root schema");
            return root.schema();
          }

          // Resolve fragment within remote document using its pointer index
          final CompiledRoot finalRootForFragment = root;
          LOG.finest(() -> "ResolverContext.resolve: Remote document pointer index keys: " + finalRootForFragment.pointerIndex().keySet());
          JsonSchema target = finalRootForFragment.pointerIndex().get(fragment);
          if (target != null) {
            LOG.finest(() -> "ResolverContext.resolve: Found fragment " + fragment + " in remote document");
            return target;
          } else {
            LOG.finest(() -> "ResolverContext.resolve: Fragment " + fragment + " not found in remote document");
            throw new IllegalArgumentException("Unresolved $ref: " + fragment);
          }
        }

        throw new IllegalStateException("Remote document not loaded: " + docUri);
      }

      throw new AssertionError("Unexpected RefToken type: " + token.getClass());
    }
  }

  /// Format validator interface for string format validation
  sealed interface FormatValidator {
    /// Test if the string value matches the format
    /// @param s the string to test
    /// @return true if the string matches the format, false otherwise
    boolean test(String s);
  }

  /// Built-in format validators
  enum Format implements FormatValidator {
    UUID {
      @Override
      public boolean test(String s) {
        try {
          java.util.UUID.fromString(s);
          return true;
        } catch (IllegalArgumentException e) {
          return false;
        }
      }
    },

    EMAIL {
      @Override
      public boolean test(String s) {
        // Pragmatic RFC-5322-lite regex: reject whitespace, require TLD, no consecutive dots
        return s.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") && !s.contains("..");
      }
    },

    IPV4 {
      @Override
      public boolean test(String s) {
        String[] parts = s.split("\\.");
        if (parts.length != 4) return false;

        for (String part : parts) {
          try {
            int num = Integer.parseInt(part);
            if (num < 0 || num > 255) return false;
            // Check for leading zeros (except for 0 itself)
            if (part.length() > 1 && part.startsWith("0")) return false;
          } catch (NumberFormatException e) {
            return false;
          }
        }
        return true;
      }
    },

    IPV6 {
      @Override
      public boolean test(String s) {
        try {
          // Use InetAddress to validate, but also check it contains ':' to distinguish from IPv4
          //noinspection ResultOfMethodCallIgnored
          java.net.InetAddress.getByName(s);
          return s.contains(":");
        } catch (Exception e) {
          return false;
        }
      }
    },

    URI {
      @Override
      public boolean test(String s) {
        try {
          java.net.URI uri = new java.net.URI(s);
          return uri.isAbsolute() && uri.getScheme() != null;
        } catch (Exception e) {
          return false;
        }
      }
    },

    URI_REFERENCE {
      @Override
      public boolean test(String s) {
        try {
          new java.net.URI(s);
          return true;
        } catch (Exception e) {
          return false;
        }
      }
    },

    HOSTNAME {
      @Override
      public boolean test(String s) {
        // Basic hostname validation: labels a-zA-Z0-9-, no leading/trailing -, label 1-63, total ≤255
        if (s.isEmpty() || s.length() > 255) return false;
        if (!s.contains(".")) return false; // Must have at least one dot

        String[] labels = s.split("\\.");
        for (String label : labels) {
          if (label.isEmpty() || label.length() > 63) return false;
          if (label.startsWith("-") || label.endsWith("-")) return false;
          if (!label.matches("^[a-zA-Z0-9-]+$")) return false;
        }
        return true;
      }
    },

    DATE {
      @Override
      public boolean test(String s) {
        try {
          java.time.LocalDate.parse(s);
          return true;
        } catch (Exception e) {
          return false;
        }
      }
    },

    TIME {
      @Override
      public boolean test(String s) {
        try {
          // Try OffsetTime first (with timezone)
          java.time.OffsetTime.parse(s);
          return true;
        } catch (Exception e) {
          try {
            // Try LocalTime (without timezone)
            java.time.LocalTime.parse(s);
            return true;
          } catch (Exception e2) {
            return false;
          }
        }
      }
    },

    DATE_TIME {
      @Override
      public boolean test(String s) {
        try {
          // Try OffsetDateTime first (with timezone)
          java.time.OffsetDateTime.parse(s);
          return true;
        } catch (Exception e) {
          try {
            // Try LocalDateTime (without timezone)
            java.time.LocalDateTime.parse(s);
            return true;
          } catch (Exception e2) {
            return false;
          }
        }
      }
    },

    REGEX {
      @Override
      public boolean test(String s) {
        try {
          java.util.regex.Pattern.compile(s);
          return true;
        } catch (Exception e) {
          return false;
        }
      }
    };

    /// Get format validator by name (case-insensitive)
    static FormatValidator byName(String name) {
      try {
        return Format.valueOf(name.toUpperCase().replace("-", "_"));
      } catch (IllegalArgumentException e) {
        return null; // Unknown format
      }
    }
  }
}
