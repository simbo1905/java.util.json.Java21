/// Copyright (c) 2025 Simon Massey
///
/// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
///
/// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
///
/// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.*;

import java.net.URI;
import java.util.*;
import java.util.logging.Logger;


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
    permits ObjectSchema,
    ArraySchema,
    StringSchema,
    NumberSchema,
    BooleanSchema,
    NullSchema,
    AnySchema,
    RefSchema,
    AllOfSchema,
    AnyOfSchema,
    OneOfSchema,
    ConditionalSchema,
    ConstSchema,
    NotSchema,
    RootRef,
    EnumSchema {

  /// Shared logger
  Logger LOG = Logger.getLogger("io.github.simbo1905.json.schema");

  /// Adapter that normalizes URI keys (strip fragment + normalize) for map access.
    record NormalizedUriMap(Map<URI, CompiledRoot> delegate) implements Map<URI, CompiledRoot> {
    private static URI norm(URI uri) {
        String s = uri.toString();
        int i = s.indexOf('#');
        URI base = i >= 0 ? URI.create(s.substring(0, i)) : uri;
        return base.normalize();
      }

    @Override
    public int size() {
      return delegate.size();
    }

    @Override
    public boolean isEmpty() {
      return delegate.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
      return key instanceof URI && delegate.containsKey(norm((URI) key));
    }

    @Override
    public boolean containsValue(Object value) {
      return delegate.containsValue(value);
    }

    @Override
    public CompiledRoot get(Object key) {
      return key instanceof URI ? delegate.get(norm((URI) key)) : null;
    }

    @Override
    public CompiledRoot put(URI key, CompiledRoot value) {
      return delegate.put(norm(key), value);
    }

    @Override
    public CompiledRoot remove(Object key) {
      return key instanceof URI ? delegate.remove(norm((URI) key)) : null;
    }

    @Override
    public void putAll(Map<? extends URI, ? extends CompiledRoot> m) {
      for (var e : m.entrySet()) delegate.put(norm(e.getKey()), e.getValue());
    }

    @Override
    public void clear() {
      delegate.clear();
    }

    @Override
    public Set<Entry<URI, CompiledRoot>> entrySet() {
      return delegate.entrySet();
    }

    @Override
    public Set<URI> keySet() {
      return delegate.keySet();
    }

    @Override
    public Collection<CompiledRoot> values() {
      return delegate.values();
    }
    }

  // Public constants for common JSON Pointer fragments used in schemas
  String SCHEMA_DEFS_POINTER = "#/$defs/";
  String SCHEMA_DEFS_SEGMENT = "/$defs/";
  String SCHEMA_PROPERTIES_SEGMENT = "/properties/";
  String SCHEMA_POINTER_PREFIX = "#/";
  String SCHEMA_POINTER_ROOT = "#";

  /// JsonSchemaOptions for schema compilation
  record JsonSchemaOptions(boolean assertFormats) {
    /// Default options with format assertion disabled
    static final JsonSchemaOptions DEFAULT = new JsonSchemaOptions(false);
    String summary() { return "assertFormats=" + assertFormats; }
  }

  /// Compile-time options controlling remote resolution and caching
  record CompileOptions(
      RemoteFetcher remoteFetcher,
      RefRegistry refRegistry,
      FetchPolicy fetchPolicy
  ) {
    static final CompileOptions DEFAULT =
        new CompileOptions(RemoteFetcher.disallowed(), RefRegistry.disallowed(), FetchPolicy.defaults());

    static CompileOptions remoteDefaults(RemoteFetcher fetcher) {
      Objects.requireNonNull(fetcher, "fetcher");
      return new CompileOptions(fetcher, RefRegistry.inMemory(), FetchPolicy.defaults());
    }

    CompileOptions withFetchPolicy(FetchPolicy policy) {
      Objects.requireNonNull(policy, "policy");
      return new CompileOptions(remoteFetcher, refRegistry, policy);
    }

    /// Delegating fetcher selecting implementation per URI scheme
    static final class DelegatingRemoteFetcher implements RemoteFetcher {
      private final Map<String, RemoteFetcher> byScheme;

      DelegatingRemoteFetcher(RemoteFetcher... fetchers) {
        Objects.requireNonNull(fetchers, "fetchers");
        if (fetchers.length == 0) {
          throw new IllegalArgumentException("At least one RemoteFetcher required");
        }
        Map<String, RemoteFetcher> map = new HashMap<>();
        for (RemoteFetcher fetcher : fetchers) {
          Objects.requireNonNull(fetcher, "fetcher");
          String scheme = Objects.requireNonNull(fetcher.scheme(), "fetcher.scheme()").toLowerCase(Locale.ROOT);
          if (scheme.isEmpty()) {
            throw new IllegalArgumentException("RemoteFetcher scheme must not be empty");
          }
          if (map.putIfAbsent(scheme, fetcher) != null) {
            throw new IllegalArgumentException("Duplicate RemoteFetcher for scheme: " + scheme);
          }
        }
        this.byScheme = Map.copyOf(map);
      }

      @Override
      public String scheme() {
        return "delegating";
      }

      @Override
      public FetchResult fetch(java.net.URI uri, FetchPolicy policy) {
        Objects.requireNonNull(uri, "uri");
        String scheme = Optional.ofNullable(uri.getScheme())
            .map(s -> s.toLowerCase(Locale.ROOT))
            .orElse("");
        RemoteFetcher fetcher = byScheme.get(scheme);
        if (fetcher == null) {
          LOG.severe(() -> "ERROR: FETCH: " + uri + " - unsupported scheme");
          throw new RemoteResolutionException(uri, RemoteResolutionException.Reason.POLICY_DENIED,
              "No RemoteFetcher registered for scheme: " + scheme);
        }
        return fetcher.fetch(uri, policy);
      }
    }
  }

  /// Remote fetcher SPI for loading external schema documents
  interface RemoteFetcher {
    String scheme();
    FetchResult fetch(java.net.URI uri, FetchPolicy policy) throws RemoteResolutionException;

    static RemoteFetcher disallowed() {
      return new RemoteFetcher() {
        @Override
        public String scheme() {
          return "<disabled>";
        }

        @Override
        public FetchResult fetch(java.net.URI uri, FetchPolicy policy) {
          LOG.severe(() -> "ERROR: FETCH: " + uri + " - policy POLICY_DENIED");
          throw new RemoteResolutionException(
              Objects.requireNonNull(uri, "uri"),
              RemoteResolutionException.Reason.POLICY_DENIED,
              "Remote fetching is disabled"
          );
        }
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

  /// Factory method to create schema from JSON Schema document
  ///
  /// @param schemaJson JSON Schema document as JsonValue
  /// @return Immutable JsonSchema instance
  /// @throws IllegalArgumentException if schema is invalid
  static JsonSchema compile(JsonValue schemaJson) {
    Objects.requireNonNull(schemaJson, "schemaJson");
    LOG.fine(() -> "compile: Starting schema compilation with default options, schema type: " + schemaJson.getClass().getSimpleName());
    JsonSchema result = compile(URI.create("urn:inmemory:root"), schemaJson, JsonSchemaOptions.DEFAULT, CompileOptions.DEFAULT);
    LOG.fine(() -> "compile: Completed schema compilation, result type: " + result.getClass().getSimpleName());
    return result;
  }

  /// Factory method to create schema from JSON Schema document with jsonSchemaOptions
  ///
  /// @param schemaJson JSON Schema document as JsonValue
  /// @param jsonSchemaOptions compilation jsonSchemaOptions
  /// @return Immutable JsonSchema instance
  /// @throws IllegalArgumentException if schema is invalid
  static JsonSchema compile(JsonValue schemaJson, JsonSchemaOptions jsonSchemaOptions) {
    Objects.requireNonNull(schemaJson, "schemaJson");
    Objects.requireNonNull(jsonSchemaOptions, "jsonSchemaOptions");
    LOG.fine(() -> "compile: Starting schema compilation with custom jsonSchemaOptions, schema type: " + schemaJson.getClass().getSimpleName());
    JsonSchema result = compile(URI.create("urn:inmemory:root"), schemaJson, jsonSchemaOptions, CompileOptions.DEFAULT);
    LOG.fine(() -> "compile: Completed schema compilation with custom jsonSchemaOptions, result type: " + result.getClass().getSimpleName());
    return result;
  }

  /// Factory method to create schema with explicit compile jsonSchemaOptions
  /// @param doc URI for the root schema document (used for $id resolution and remote $ref)
  /// @param schemaJson Parsed JSON Schema document as JsonValue
  /// @param jsonSchemaOptions compilation jsonSchemaOptions
  /// @param compileOptions compilation compileOptions
  static JsonSchema compile(URI doc, JsonValue schemaJson, JsonSchemaOptions jsonSchemaOptions, CompileOptions compileOptions) {
    Objects.requireNonNull(doc, "initialContext must not be null");
    Objects.requireNonNull(schemaJson, "schemaJson must not be null");
    Objects.requireNonNull(jsonSchemaOptions, "jsonSchemaOptions must not be null");
    Objects.requireNonNull(compileOptions, "compileOptions must not be null");
    LOG.fine(() -> "JsonSchema.compile start doc="+ doc +
        ", jsonSchemaOptions=" + jsonSchemaOptions.summary() +
        ", schema type: " + schemaJson.getClass().getSimpleName() +
        ", jsonSchemaOptions.assertFormats=" + jsonSchemaOptions.assertFormats() +
        ", compileOptions.remoteFetcher=" + compileOptions.remoteFetcher().getClass().getSimpleName() +
        ", fetch policy allowedSchemes=" + compileOptions.fetchPolicy().allowedSchemes());

    // Early policy enforcement for root-level remote $ref to avoid unnecessary work
    // FIXME this is an unnecessary optimization at compile time we should just be optimistic and inline this to the main loop
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
          // FIXME this feels unsafe lets fail fast here
          // Not a URI, ignore - normal compilation will handle it
        }
      }
    }

    // Placeholder context (not used post-compile; schemas embed resolver contexts during build)
    Map<URI, CompiledRoot> emptyRoots = new LinkedHashMap<>();
    Map<String, JsonSchema> emptyPointerIndex = new LinkedHashMap<>();
    ResolverContext context = new ResolverContext(emptyRoots, emptyPointerIndex, AnySchema.INSTANCE);

    // Compile using work-stack architecture – contexts are attached once while compiling
    CompiledRegistry registry = compileWorkStack(
        schemaJson,
        doc,
        context,
        jsonSchemaOptions,
        compileOptions
    );
    JsonSchema result = registry.entry().schema();
    final int rootCount = registry.roots().size();

    // Compile-time validation for root-level remote $ref pointer existence
    if (result instanceof RefSchema(RefToken refToken, ResolverContext resolverContext)) {
      if (refToken instanceof RefToken.RemoteRef remoteRef) {
        String frag = remoteRef.pointer();
        if (!frag.isEmpty()) {
          try {
            // Attempt resolution now via the ref's own context to surface POINTER_MISSING during compile
            resolverContext.resolve(refToken);
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

    LOG.fine(() -> "json-schema.compile done   roots=" + rootCount);
    return result;
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
      LOG.severe(() -> "ERROR: SCHEMA: normalizeUri failed ref=" + refString + " base=" + baseUri);
      throw new IllegalArgumentException("Invalid URI reference: " + refString);
    }
  }

  /// Core work-stack compilation loop
  static CompiledRegistry compileWorkStack(JsonValue initialJson,
                                           java.net.URI initialUri,
                                           ResolverContext context,
                                           JsonSchemaOptions jsonSchemaOptions,
                                           CompileOptions compileOptions) {
    LOG.fine(() -> "compileWorkStack: starting work-stack loop with initialUri=" + initialUri);
    LOG.finest(() -> "compileWorkStack: initialJson object=" + initialJson + ", type=" + initialJson.getClass().getSimpleName() + ", content=" + initialJson +
        ", initialUri object=" + initialUri + ", scheme=" + initialUri.getScheme() + ", host=" + initialUri.getHost() + ", path=" + initialUri.getPath());

    // Work stack (LIFO) for documents to compile
    Deque<java.net.URI> workStack = new ArrayDeque<>();
    Map<java.net.URI, CompiledRoot> built = new NormalizedUriMap(new LinkedHashMap<>());
    Set<java.net.URI> active = new HashSet<>();
    Map<java.net.URI, java.net.URI> parentMap = new HashMap<>();

    // Push initial document
    workStack.push(initialUri);
    LOG.finest(() -> "compileWorkStack: workStack after push=" + workStack + ", contents=" + workStack.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(", ", "[", "]")));

    int iterationCount = 0;
    while (!workStack.isEmpty()) {
      iterationCount++;
      final int finalIterationCount = iterationCount;
      final int workStackSize = workStack.size();
      final int builtSize = built.size();
      final int activeSize = active.size();

      java.net.URI currentUri = workStack.pop();
      LOG.finer(() -> "compileWorkStack.iteration iter=" + finalIterationCount + " workStack=" + workStackSize + " built=" + builtSize + " active=" + activeSize);

      // Check for cycles
      detectAndThrowCycle(active, currentUri, "compile-time remote ref cycle");

      // Skip if already compiled
      if (built.containsKey(currentUri)) {
        LOG.finer(() -> "compileWorkStack: URI already compiled, skipping: " + currentUri);
        continue;
      }

      active.add(currentUri);

      LOG.finest(() -> "compileWorkStack: added URI to active set, active now=" + active);
      try {
        // Fetch document if needed
        JsonValue documentJson = fetchIfNeeded(currentUri, initialUri, initialJson, context, compileOptions);
        LOG.finest(() -> "compileWorkStack: fetched documentJson object=" + documentJson + ", type=" + documentJson.getClass().getSimpleName() + ", content=" + documentJson);

        // Use the new MVF compileBundle method that properly handles remote refs
        CompilationBundle bundle = SchemaCompiler.compileBundle(
            documentJson,
            jsonSchemaOptions,
            compileOptions
        );

        // Get the compiled schema from the bundle
        JsonSchema schema = bundle.entry().schema();
        LOG.finest(() -> "buildRoot: compiled schema object=" + schema + ", class=" + schema.getClass().getSimpleName());

        // Register all compiled roots from the bundle into the global built map
        LOG.finest(() -> "buildRoot: registering " + bundle.all().size() + " compiled roots from bundle into global registry");
        for (CompiledRoot compiledRoot : bundle.all()) {
          URI rootUri = compiledRoot.docUri();
          LOG.finest(() -> "buildRoot: registering compiled root for URI: " + rootUri);
          built.put(rootUri, compiledRoot);
          LOG.fine(() -> "buildRoot: registered compiled root for URI: " + rootUri);
        }

        LOG.fine(() -> "buildRoot: built registry now has " + built.size() + " roots: " + built.keySet());

        // Process any discovered refs from the compilation
        // The compileBundle method should have already processed remote refs through the work stack
        LOG.finer(() -> "buildRoot: MVF compilation completed, work stack processed remote refs");
        LOG.finer(() -> "buildRoot: completed for docUri=" + currentUri + ", schema type=" + schema.getClass().getSimpleName());
        JsonSchema rootSchema = schema;
        LOG.finest(() -> "compileWorkStack: built rootSchema object=" + rootSchema + ", class=" + rootSchema.getClass().getSimpleName());
      } finally {
        active.remove(currentUri);
        LOG.finest(() -> "compileWorkStack: removed URI from active set, active now=" + active);
      }
    }

    // Freeze roots into immutable registry (preserve entry root as initialUri)
    CompiledRegistry registry = freezeRoots(built, initialUri);
    LOG.fine(() -> "compileWorkStack.done roots=" + registry.roots().size());
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
    LOG.finest(() -> "fetchIfNeeded: docUri object=" + docUri + ", scheme=" + docUri.getScheme() + ", host=" + docUri.getHost() + ", path=" + docUri.getPath() +
        ", initialUri object=" + initialUri + ", scheme=" + initialUri.getScheme() + ", host=" + initialUri.getHost() + ", path=" + initialUri.getPath() +
        ", initialJson object=" + initialJson + ", type=" + initialJson.getClass().getSimpleName() + ", content=" + initialJson +
        ", context object=" + context + ", roots.size=" + context.roots().size() + ", localPointerIndex.size=" + context.localPointerIndex().size());

    if (docUri.equals(initialUri)) {
      LOG.finer(() -> "fetchIfNeeded: using initial JSON for primary document");
      return initialJson;
    }

    // MVF: Fetch remote document using RemoteFetcher from compile options
    LOG.finer(() -> "fetchIfNeeded: fetching remote document: " + docUri);
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

    RemoteFetcher.FetchResult fetchResult =
        compileOptions.remoteFetcher().fetch(docUriWithoutFragment, compileOptions.fetchPolicy());
    JsonValue fetchedDocument = fetchResult.document();

    LOG.finer(() -> "fetchIfNeeded: successfully fetched remote document: " + docUriWithoutFragment + ", document type: " + fetchedDocument.getClass().getSimpleName());
    return fetchedDocument;
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
  static boolean scheduleRemoteIfUnseen(Deque<java.net.URI> workStack,
                                        Map<java.net.URI, CompiledRoot> built,
                                        Map<java.net.URI, java.net.URI> parentMap,
                                        java.net.URI currentDocUri,
                                        java.net.URI targetDocUri) {
    LOG.finer(() -> "scheduleRemoteIfUnseen: target=" + targetDocUri + ", workStack.size=" + workStack.size() + ", built.size=" + built.size());
    LOG.finest(() -> "scheduleRemoteIfUnseen: targetDocUri object=" + targetDocUri + ", scheme=" + targetDocUri.getScheme() + ", host=" + targetDocUri.getHost() + ", path=" + targetDocUri.getPath());
    LOG.finest(() -> "scheduleRemoteIfUnseen: workStack object=" + workStack + ", contents=" + workStack.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(", ", "[", "]")));
    LOG.finest(() -> "scheduleRemoteIfUnseen: built map object=" + built + ", keys=" + built.keySet() + ", size=" + built.size());

    // Detect remote cycles by walking parent chain
    if (SchemaCompiler.formsRemoteCycle(parentMap, currentDocUri, targetDocUri)) {
      String cycleMessage = "ERROR: CYCLE: remote $ref cycle detected current=" + currentDocUri + ", target=" + targetDocUri;
      LOG.severe(() -> cycleMessage);
      throw new IllegalStateException(cycleMessage);
    }

    // Check if already built or already in work stack
    boolean alreadyBuilt = built.containsKey(targetDocUri);
    boolean inWorkStack = workStack.contains(targetDocUri);
    LOG.finest(() -> "scheduleRemoteIfUnseen: alreadyBuilt=" + alreadyBuilt + ", inWorkStack=" + inWorkStack);

    if (alreadyBuilt || inWorkStack) {
      LOG.finer(() -> "scheduleRemoteIfUnseen: already seen, skipping");
      LOG.finest(() -> "scheduleRemoteIfUnseen: skipping targetDocUri=" + targetDocUri);
      return false;
    }

    // Track parent chain for cycle detection before scheduling child
    parentMap.putIfAbsent(targetDocUri, currentDocUri);

    // Add to work stack
    workStack.push(targetDocUri);
    LOG.finer(() -> "scheduleRemoteIfUnseen: scheduled remote document: " + targetDocUri);
    LOG.finest(() -> "scheduleRemoteIfUnseen: workStack after push=" + workStack + ", contents=" + workStack.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(", ", "[", "]")));
    return true;
  }

  /// Detect and throw on compile-time cycles
  static void detectAndThrowCycle(Set<java.net.URI> active, java.net.URI docUri, String pathTrail) {
    LOG.finest(() -> "detectAndThrowCycle: active set=" + active + ", docUri object=" + docUri + ", scheme=" + docUri.getScheme() + ", host=" + docUri.getHost() + ", path=" + docUri.getPath() + ", pathTrail='" + pathTrail + "'");
    if (active.contains(docUri)) {
      String cycleMessage = "ERROR: CYCLE: " + pathTrail + "; doc=" + docUri;
      LOG.severe(() -> cycleMessage);
      throw new IllegalArgumentException(cycleMessage);
    }
    LOG.finest(() -> "detectAndThrowCycle: no cycle detected");
  }

  /// Freeze roots into immutable registry
  static CompiledRegistry freezeRoots(Map<java.net.URI, CompiledRoot> built, java.net.URI primaryUri) {
    LOG.finer(() -> "freezeRoots: freezing " + built.size() + " compiled roots, built map object=" + built + ", keys=" + built.keySet() + ", values=" + built.values());

    // Find entry root by the provided primary URI
    CompiledRoot entryRoot = built.get(primaryUri);
    if (entryRoot == null) {
      // Fallback: if not found, attempt to get by base URI without fragment
      java.net.URI alt = java.net.URI.create(primaryUri.toString());
      entryRoot = built.get(alt);
    }
    if (entryRoot == null) {
      // As a last resort, pick the first element to avoid NPE, but log an error
      LOG.severe(() -> "ERROR: SCHEMA: primary root not found doc=" + primaryUri);
      entryRoot = built.values().iterator().next();
    }
    final java.net.URI primaryResolved = entryRoot.docUri();
    final java.net.URI entryDocUri = entryRoot.docUri();
    final String entrySchemaType = entryRoot.schema().getClass().getSimpleName();
    LOG.finest(() -> "freezeRoots: entryRoot docUri=" + entryDocUri + ", schemaType=" + entrySchemaType + ", primaryUri object=" + primaryResolved + ", scheme=" + primaryResolved.getScheme() + ", host=" + primaryResolved.getHost() + ", path=" + primaryResolved.getPath());

    LOG.fine(() -> "freezeRoots: primary root URI: " + primaryResolved);

    // Create immutable map
    Map<java.net.URI, CompiledRoot> frozenRoots = Map.copyOf(built);
    LOG.finest(() -> "freezeRoots: frozenRoots map object=" + frozenRoots + ", keys=" + frozenRoots.keySet() + ", values=" + frozenRoots.values() + ", size=" + frozenRoots.size());

    CompiledRegistry registry = new CompiledRegistry(frozenRoots, entryRoot);
    LOG.finest(() -> "freezeRoots: created CompiledRegistry object=" + registry + ", entry=" + registry.entry() + ", roots.size=" + registry.roots().size());
    return registry;
  }

  /// Validates JSON document against this schema
  ///
  /// @param json JSON value to validate
  /// @return ValidationResult with success/failure information
  default ValidationResult validate(JsonValue json) {
    Objects.requireNonNull(json, "json");
    LOG.fine(() -> "json-schema.validate start frames=0 doc=unknown");
    List<ValidationError> errors = new ArrayList<>();
    Deque<ValidationFrame> stack = new ArrayDeque<>();
    Set<ValidationKey> visited = new HashSet<>();
    stack.push(new ValidationFrame("", this, json));

    int iterationCount = 0;
    int maxDepthObserved = 0;
    final int WARNING_THRESHOLD = 10_000;

    while (!stack.isEmpty()) {
      iterationCount++;
      if (stack.size() > maxDepthObserved) maxDepthObserved = stack.size();
      if (iterationCount % WARNING_THRESHOLD == 0) {
        final int processed = iterationCount;
        final int pending = stack.size();
        final int maxDepth = maxDepthObserved;
        LOG.fine(() -> "PERFORMANCE WARNING: Validation stack processed=" + processed + " pending=" + pending + " maxDepth=" + maxDepth);
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
    record ValidationKey(JsonSchema schema, JsonValue json, String path) {

    @Override
      public boolean equals(Object obj) {
        if (this == obj) {
          return true;
        }
        if (!(obj instanceof ValidationKey(JsonSchema schema1, JsonValue json1, String path1))) {
          return false;
        }
        return this.schema == schema1 &&
            this.json == json1 &&
            Objects.equals(this.path, path1);
      }

      @Override
      public int hashCode() {
        int result = System.identityHashCode(schema);
        result = 31 * result + System.identityHashCode(json);
        result = 31 * result + (path != null ? path.hashCode() : 0);
        return result;
      }
    }

  /// Compiled registry holding multiple schema roots
  record CompiledRegistry(
      java.util.Map<java.net.URI, CompiledRoot> roots,
      CompiledRoot entry
  ) {
  }

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

}
