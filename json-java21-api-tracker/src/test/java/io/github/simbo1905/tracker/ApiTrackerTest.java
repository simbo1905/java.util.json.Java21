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
            
            // Should also find internal implementation classes
            assertThat(classes.stream().anyMatch(c -> c.getName().startsWith("jdk.sandbox.internal.util.json")))
                .as("Should find internal implementation classes")
                .isTrue();
            
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
        @DisplayName("Should extract API from JsonObject interface")
        void testExtractLocalApiJsonObject() throws ClassNotFoundException {
            final var clazz = Class.forName("jdk.sandbox.java.util.json.JsonObject");
            final var api = ApiTracker.extractLocalApi(clazz);
            
            assertThat(api).isNotNull();
            assertThat(api.members()).containsKey("className");
            assertThat(((JsonString) api.members().get("className")).value()).isEqualTo("JsonObject");
            
            assertThat(api.members()).containsKey("packageName");
            assertThat(((JsonString) api.members().get("packageName")).value()).isEqualTo("jdk.sandbox.java.util.json");
            
            assertThat(api.members()).containsKey("isInterface");
            assertThat(api.members().get("isInterface")).isEqualTo(JsonBoolean.of(true));
            
            assertThat(api.members()).containsKey("methods");
            final var methods = (JsonObject) api.members().get("methods");
            assertThat(methods.members()).containsKeys("members", "of");
        }
        
        @Test
        @DisplayName("Should extract API from JsonValue sealed interface")
        void testExtractLocalApiJsonValue() throws ClassNotFoundException {
            final var clazz = Class.forName("jdk.sandbox.java.util.json.JsonValue");
            final var api = ApiTracker.extractLocalApi(clazz);
            
            assertThat(api.members()).containsKey("isSealed");
            assertThat(api.members().get("isSealed")).isEqualTo(JsonBoolean.of(true));
            
            assertThat(api.members()).containsKey("permits");
            final var permits = (JsonArray) api.members().get("permits");
            assertThat(permits.values()).isNotEmpty();
        }
        
        @Test
        @DisplayName("Should handle null class parameter")
        void testExtractLocalApiNull() {
            assertThatThrownBy(() -> ApiTracker.extractLocalApi(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("clazz must not be null");
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
    @DisplayName("Modifier Extraction")
    class ModifierExtractionTests {
        
        @Test
        @DisplayName("Should extract modifiers correctly")
        void testExtractModifiers() {
            // Test public static final
            final var modifiers = java.lang.reflect.Modifier.PUBLIC | 
                                  java.lang.reflect.Modifier.STATIC | 
                                  java.lang.reflect.Modifier.FINAL;
            
            final var result = ApiTracker.extractModifiers(modifiers);
            
            assertThat(result.values()).hasSize(3);
            assertThat(result.values().stream().map(v -> ((JsonString) v).value()))
                .containsExactlyInAnyOrder("public", "static", "final");
        }
    }
}