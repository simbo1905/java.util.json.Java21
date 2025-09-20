package io.github.simbo1905.json.schema;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import static io.github.simbo1905.json.schema.SchemaLogging.LOG;

/// Test utility for handling file:// URLs in remote reference tests
/// Provides consistent path resolution and configuration for test resources
public final class TestResourceUtils {
    
    /// Base directory for test resources - configurable via system property
    private static final String TEST_RESOURCE_BASE = System.getProperty(
        "json.schema.test.resources", 
        "src/test/resources"
    );
    
    /// Working directory for tests - defaults to module root
    private static final String TEST_WORKING_DIR = System.getProperty(
        "json.schema.test.workdir",
        "."
    );
    
    static {
        // Log configuration at CONFIG level for debugging
        LOG.config(() -> "Test Resource Configuration:");
        LOG.config(() -> "  TEST_RESOURCE_BASE: " + TEST_RESOURCE_BASE);
        LOG.config(() -> "  TEST_WORKING_DIR: " + TEST_WORKING_DIR);
        LOG.config(() -> "  Absolute resource base: " + Paths.get(TEST_RESOURCE_BASE).toAbsolutePath());
    }
    
    /// Get a file:// URI for a test resource file
    /// @param testClass The test class name (e.g., "JsonSchemaRemoteRefTest")
    /// @param testMethod The test method name (e.g., "resolves_http_ref")
    /// @param filename The filename within the test method directory
    /// @return A file:// URI pointing to the test resource
    public static URI getTestResourceUri(String testClass, String testMethod, String filename) {
        Path resourcePath = Paths.get(TEST_RESOURCE_BASE, testClass, testMethod, filename);
        Path absolutePath = resourcePath.toAbsolutePath();
        
        LOG.config(() -> "Resolving test resource: " + testClass + "/" + testMethod + "/" + filename);
        LOG.config(() -> "  Resource path: " + resourcePath);
        LOG.config(() -> "  Absolute path: " + absolutePath);
        
        if (!absolutePath.toFile().exists()) {
            LOG.severe(() -> "ERROR: SCHEMA: test resource not found path=" + absolutePath);
            throw new IllegalArgumentException("Test resource not found: " + absolutePath);
        }
        
        URI fileUri = absolutePath.toUri();
        LOG.config(() -> "  File URI: " + fileUri);
        return fileUri;
    }
    
    /// Get a file:// URI for a test resource file using simplified naming
    /// @param relativePath Path relative to test resources (e.g., "JsonSchemaRemoteRefTest/a.json")
    /// @return A file:// URI pointing to the test resource
    public static URI getTestResourceUri(String relativePath) {
        Path resourcePath = Paths.get(TEST_RESOURCE_BASE, relativePath);
        Path absolutePath = resourcePath.toAbsolutePath();
        
        LOG.config(() -> "Resolving test resource: " + relativePath);
        LOG.config(() -> "  Resource path: " + resourcePath);
        LOG.config(() -> "  Absolute path: " + absolutePath);
        
        if (!absolutePath.toFile().exists()) {
            LOG.severe(() -> "ERROR: SCHEMA: test resource not found path=" + absolutePath);
            throw new IllegalArgumentException("Test resource not found: " + absolutePath);
        }
        
        URI fileUri = absolutePath.toUri();
        LOG.config(() -> "  File URI: " + fileUri);
        return fileUri;
    }
    
    /// Convert an HTTP URL to a file:// URL for testing
    /// @param httpUrl The original HTTP URL (e.g., "http://host/a.json")
    /// @param testClass The test class name
    /// @param testMethod The test method name  
    /// @return A corresponding file:// URL
    public static URI convertHttpToFileUrl(String httpUrl, String testClass, String testMethod) {
        // Extract path from HTTP URL (remove host)
        String path = httpUrl.replace("http://host", "");
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        
        String filename = path.isEmpty() ? "index.json" : path;
        return getTestResourceUri(testClass, testMethod, filename);
    }
    
    /// Convert an HTTP URL to a file:// URL using simplified naming
    /// @param httpUrl The original HTTP URL (e.g., "http://host/a.json")
    /// @param relativePath The relative path in test resources (e.g., "JsonSchemaRemoteRefTest/a.json")
    /// @return A corresponding file:// URL
    public static URI convertHttpToFileUrl(String httpUrl, String relativePath) {
        return getTestResourceUri(relativePath);
    }
    
    private TestResourceUtils() {
        // Utility class, prevent instantiation
    }
}
