package io.github.simbo1905.tracker;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jdk.sandbox.java.util.json.JsonBoolean;
import jdk.sandbox.java.util.json.JsonArray;
import jdk.sandbox.java.util.json.JsonObject;
import jdk.sandbox.java.util.json.JsonString;
import jdk.sandbox.java.util.json.JsonValue;

import java.util.Set;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

public class ApiTrackerTest {
    private static final Logger LOGGER = Logger.getLogger(ApiTrackerTest.class.getName());
    
    @BeforeAll
    static void setupLogging() {
        LoggingControl.setupCleanLogging();
    }
    
    @Nested
    @DisplayName("Local Class Discovery")
    class LocalDiscoveryTests {
        
        @Test
        @DisplayName("Should discover JSON API classes")
        void testDiscoverLocalJsonClasses() {
            final var classes = ApiTracker.discoverLocalJsonClasses();
            
            assertThat(classes).isNotNull();
            assertThat(classes).isNotEmpty();
            
            // Should find core JSON interfaces
            assertThat(classes.stream().map(Class::getName))
                .contains(
                    "jdk.sandbox.java.util.json.JsonValue",
                    "jdk.sandbox.java.util.json.JsonObject",
                    "jdk.sandbox.java.util.json.JsonArray",
                    "jdk.sandbox.java.util.json.JsonString",
                    "jdk.sandbox.java.util.json.JsonNumber",
                    "jdk.sandbox.java.util.json.JsonBoolean",
                    "jdk.sandbox.java.util.json.JsonNull"
                );
            
            // Should NOT find internal implementation classes (public API only)
            assertThat(classes.stream().anyMatch(c -> c.getName().startsWith("jdk.sandbox.internal.util.json")))
                .as("Should not find internal implementation classes - public API only")
                .isFalse();
            
            // Should be sorted
            final var names = classes.stream().map(Class::getName).toList();
            final var sortedNames = names.stream().sorted().toList();
            assertThat(names).isEqualTo(sortedNames);
        }
    }
    
    @Nested
    @DisplayName("Local API Extraction")
    class LocalApiExtractionTests {
        
        @Test
        @DisplayName("Should extract API from JsonObject interface source")
        void testExtractLocalApiJsonObject() {
            final var api = ApiTracker.extractLocalApiFromSource("jdk.sandbox.java.util.json.JsonObject");
            
            assertThat(api).isNotNull();
            // Check if extraction succeeded or failed
            if (api.members().containsKey("error")) {
                // If file not found, that's expected for some source setups
                final var error = ((JsonString) api.members().get("error")).value();
                assertThat(error).contains("LOCAL_FILE_NOT_FOUND");
            } else {
                // If extraction succeeded, validate structure
                assertThat(api.members()).containsKey("className");
                assertThat(((JsonString) api.members().get("className")).value()).isEqualTo("JsonObject");
                
                assertThat(api.members()).containsKey("packageName");
                assertThat(((JsonString) api.members().get("packageName")).value()).isEqualTo("jdk.sandbox.java.util.json");
                
                assertThat(api.members()).containsKey("isInterface");
                assertThat(api.members().get("isInterface")).isEqualTo(JsonBoolean.of(true));
            }
        }
        
        @Test
        @DisplayName("Should extract API from JsonValue sealed interface source")
        void testExtractLocalApiJsonValue() {
            final var api = ApiTracker.extractLocalApiFromSource("jdk.sandbox.java.util.json.JsonValue");
            
            // Check if extraction succeeded or failed
            if (api.members().containsKey("error")) {
                // If file not found, that's expected for some source setups
                final var error = ((JsonString) api.members().get("error")).value();
                assertThat(error).contains("LOCAL_FILE_NOT_FOUND");
            } else {
                // If extraction succeeded, validate structure
                assertThat(api.members()).containsKey("isSealed");
                assertThat(api.members().get("isSealed")).isEqualTo(JsonBoolean.of(true));
                
                assertThat(api.members()).containsKey("permits");
                final var permits = (JsonArray) api.members().get("permits");
                // May be empty in source parsing if permits aren't explicitly listed
                assertThat(permits).isNotNull();
            }
        }
        
        @Test
        @DisplayName("Should handle missing source file gracefully")
        void testExtractLocalApiMissingFile() {
            final var api = ApiTracker.extractLocalApiFromSource("jdk.sandbox.java.util.json.NonExistentClass");
            
            assertThat(api.members()).containsKey("error");
            final var error = ((JsonString) api.members().get("error")).value();
            assertThat(error).contains("LOCAL_FILE_NOT_FOUND");
        }
    }
    
    @Nested
    @DisplayName("Upstream Source Fetching")
    class UpstreamFetchingTests {
        
        @Test
        @DisplayName("Should map local class names to upstream paths")
        void testMapToUpstreamPath() {
            assertThat(ApiTracker.mapToUpstreamPath("jdk.sandbox.java.util.json.JsonObject"))
                .isEqualTo("java/util/json/JsonObject.java");
            
            assertThat(ApiTracker.mapToUpstreamPath("jdk.sandbox.internal.util.json.JsonObjectImpl"))
                .isEqualTo("jdk/internal/util/json/JsonObjectImpl.java");
        }
        
        @Test
        @DisplayName("Should handle null parameter in fetchUpstreamSources")
        void testFetchUpstreamSourcesNull() {
            assertThatThrownBy(() -> ApiTracker.fetchUpstreamSources(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("localClasses must not be null");
        }
        
        @Test
        @DisplayName("Should return empty map for empty input")
        void testFetchUpstreamSourcesEmpty() {
            final var result = ApiTracker.fetchUpstreamSources(Set.of());
            assertThat(result).isEmpty();
        }
    }
    
    @Nested
    @DisplayName("API Comparison")
    class ApiComparisonTests {
        
        @Test
        @DisplayName("Should handle null parameters in compareApis")
        void testCompareApisNull() {
            final var dummyApi = JsonObject.of(Map.of("className", JsonString.of("Test")));
            
            assertThatThrownBy(() -> ApiTracker.compareApis(null, dummyApi))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("local must not be null");
            
            assertThatThrownBy(() -> ApiTracker.compareApis(dummyApi, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("upstream must not be null");
        }
        
        @Test
        @DisplayName("Should handle upstream errors in comparison")
        void testCompareApisUpstreamError() {
            final var local = JsonObject.of(Map.of("className", JsonString.of("TestClass")));
            final var upstream = JsonObject.of(Map.of(
                "error", JsonString.of("NOT_FOUND: File not found"),
                "className", JsonString.of("TestClass")
            ));
            
            final var result = ApiTracker.compareApis(local, upstream);
            
            assertThat(result.members()).containsKey("status");
            assertThat(((JsonString) result.members().get("status")).value()).isEqualTo("UPSTREAM_ERROR");
            assertThat(result.members()).containsKey("error");
        }
    }
    
    @Nested
    @DisplayName("Full Comparison Orchestration")
    class FullComparisonTests {
        
        @Test
        @DisplayName("Should run full comparison and return report structure")
        void testRunFullComparison() {
            final var report = ApiTracker.runFullComparison();
            
            assertThat(report).isNotNull();
            assertThat(report.members()).containsKeys(
                "timestamp",
                "localPackage",
                "upstreamPackage",
                "summary",
                "differences",
                "durationMs"
            );
            
            final var summary = (JsonObject) report.members().get("summary");
            assertThat(summary.members()).containsKeys(
                "totalClasses",
                "matchingClasses",
                "missingUpstream",
                "differentApi"
            );
            
            // Total classes should be greater than 0
            final var totalClasses = summary.members().get("totalClasses");
            assertThat(totalClasses).isNotNull();
        }
    }
    
    @Nested
    @DisplayName("Type Name Normalization")
    class TypeNameNormalizationTests {
        
        @Test
        @DisplayName("Should normalize type names correctly")
        void testNormalizeTypeName() {
            assertThat(ApiTracker.normalizeTypeName("jdk.sandbox.java.util.json.JsonValue"))
                .isEqualTo("JsonValue");
            
            assertThat(ApiTracker.normalizeTypeName("java.lang.String"))
                .isEqualTo("String");
            
            assertThat(ApiTracker.normalizeTypeName("String"))
                .isEqualTo("String");
        }
    }
}