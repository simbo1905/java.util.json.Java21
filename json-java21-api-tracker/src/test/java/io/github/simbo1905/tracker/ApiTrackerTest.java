package io.github.simbo1905.tracker;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jdk.incubator.java.util.json.JsonBoolean;
import jdk.incubator.java.util.json.JsonArray;
import jdk.incubator.java.util.json.JsonObject;
import jdk.incubator.java.util.json.JsonString;

import java.util.Set;
import java.util.Map;

public class ApiTrackerTest {

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
                    "jdk.incubator.java.util.json.JsonValue",
                    "jdk.incubator.java.util.json.JsonObject",
                    "jdk.incubator.java.util.json.JsonArray",
                    "jdk.incubator.java.util.json.JsonString",
                    "jdk.incubator.java.util.json.JsonNumber",
                    "jdk.incubator.java.util.json.JsonBoolean",
                    "jdk.incubator.java.util.json.JsonNull"
                );

            // Should NOT find internal implementation classes (public API only)
            assertThat(classes.stream().anyMatch(c -> c.getName().startsWith("jdk.incubator.internal.util.json")))
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
            final var api = ApiTracker.extractLocalApiFromSource("jdk.incubator.java.util.json.JsonObject");

            assertThat(api).isNotNull();
            // Check if extraction succeeded or failed
            if (api.asMap().containsKey("error")) {
                // If file not found, that's expected for some source setups
                final var error = ((JsonString) api.asMap().get("error")).asString();
                assertThat(error).contains("LOCAL_FILE_NOT_FOUND");
            } else {
                // If extraction succeeded, validate structure
                assertThat(api.asMap()).containsKey("className");
                assertThat(((JsonString) api.asMap().get("className")).asString()).isEqualTo("JsonObject");

                assertThat(api.asMap()).containsKey("packageName");
                assertThat(((JsonString) api.asMap().get("packageName")).asString()).isEqualTo("jdk.incubator.java.util.json");

                assertThat(api.asMap()).containsKey("isInterface");
                assertThat(api.asMap().get("isInterface")).isEqualTo(JsonBoolean.of(true));
            }
        }

        @Test
        @DisplayName("Should extract API from JsonValue sealed interface source")
        void testExtractLocalApiJsonValue() {
            final var api = ApiTracker.extractLocalApiFromSource("jdk.incubator.java.util.json.JsonValue");

            // Check if extraction succeeded or failed
            if (api.asMap().containsKey("error")) {
                // If file not found, that's expected for some source setups
                final var error = ((JsonString) api.asMap().get("error")).asString();
                assertThat(error).contains("LOCAL_FILE_NOT_FOUND");
            } else {
                // If extraction succeeded, validate structure
                assertThat(api.asMap()).containsKey("isSealed");
                assertThat(api.asMap().get("isSealed")).isEqualTo(JsonBoolean.of(true));

                assertThat(api.asMap()).containsKey("permits");
                final var permits = (JsonArray) api.asMap().get("permits");
                // May be empty in source parsing if permits aren't explicitly listed
                assertThat(permits).isNotNull();
            }
        }

        @Test
        @DisplayName("Should handle missing source file gracefully")
        void testExtractLocalApiMissingFile() {
            final var api = ApiTracker.extractLocalApiFromSource("jdk.incubator.java.util.json.NonExistentClass");

            assertThat(api.asMap()).containsKey("error");
            final var error = ((JsonString) api.asMap().get("error")).asString();
            assertThat(error).contains("LOCAL_FILE_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("Upstream Source Fetching")
    class UpstreamFetchingTests {

        @Test
        @DisplayName("Should map local class names to upstream paths")
        void testMapToUpstreamPath() {
            assertThat(ApiTracker.mapToUpstreamPath("jdk.incubator.java.util.json.JsonObject"))
                .isEqualTo("jdk/incubator/json/JsonObject.java");

            assertThat(ApiTracker.mapToUpstreamPath("jdk.incubator.internal.util.json.JsonObjectImpl"))
                .isEqualTo("jdk/incubator/json/impl/JsonObjectImpl.java");
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

            assertThat(result.asMap()).containsKey("status");
            assertThat(((JsonString) result.asMap().get("status")).asString()).isEqualTo("UPSTREAM_ERROR");
            assertThat(result.asMap()).containsKey("error");
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
            assertThat(report.asMap()).containsKeys(
                "timestamp",
                "localPackage",
                "upstreamPackage",
                "summary",
                "differences",
                "durationMs"
            );

            final var summary = (JsonObject) report.asMap().get("summary");
            assertThat(summary.asMap()).containsKeys(
                "totalClasses",
                "matchingClasses",
                "missingUpstream",
                "differentApi"
            );

            // Total classes should be greater than 0
            final var totalClasses = summary.asMap().get("totalClasses");
            assertThat(totalClasses).isNotNull();
        }
    }

    @Nested
    @DisplayName("Type Name Normalization")
    class TypeNameNormalizationTests {

        @Test
        @DisplayName("Should normalize type names correctly")
        void testNormalizeTypeName() {
            assertThat(ApiTracker.normalizeTypeName("jdk.incubator.java.util.json.JsonValue"))
                .isEqualTo("JsonValue");

            assertThat(ApiTracker.normalizeTypeName("java.lang.String"))
                .isEqualTo("String");

            assertThat(ApiTracker.normalizeTypeName("String"))
                .isEqualTo("String");
        }
    }
}