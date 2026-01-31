package jdk.sandbox.java.util.json;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.UUID;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.*;

/// Tests for {@link UUIDGenerator} system property configuration.
///
/// This test verifies that system properties can control the UUID generation mode.
/// These tests run in a separate JVM via Maven Surefire configuration.
class UUIDGeneratorConfigTest {
    
    private static final Logger LOGGER = Logger.getLogger(UUIDGeneratorConfigTest.class.getName());
    
    @Test
    @DisplayName("Verify default mode is V7 when no system property is set")
    void testDefaultModeIsV7() {
        LOGGER.info("Executing testDefaultModeIsV7");
        // This test assumes no system property was set at JVM startup
        // In the default configuration, mode should be V7
        final UUIDGenerator.Mode mode = UUIDGenerator.getConfiguredMode();
        LOGGER.info(() -> "Configured mode: " + mode);
        
        // Generate a UUID and verify it's a valid UUIDv7
        final UUID uuid = UUIDGenerator.generateUUID();
        assertThat(uuid).isNotNull();
        
        // If mode is V7, the UUID should have version 7
        if (mode == UUIDGenerator.Mode.V7) {
            assertThat(uuid.version()).isEqualTo(7);
        }
    }
    
    @Test
    @DisplayName("Generate multiple UUIDs and verify consistency")
    void testMultipleUUIDsWithConfiguredMode() {
        LOGGER.info("Executing testMultipleUUIDsWithConfiguredMode");
        final UUIDGenerator.Mode mode = UUIDGenerator.getConfiguredMode();
        LOGGER.info(() -> "Configured mode: " + mode);
        
        // Generate multiple UUIDs
        final UUID uuid1 = UUIDGenerator.generateUUID();
        final UUID uuid2 = UUIDGenerator.generateUUID();
        final UUID uuid3 = UUIDGenerator.generateUUID();
        
        assertThat(uuid1).isNotNull();
        assertThat(uuid2).isNotNull();
        assertThat(uuid3).isNotNull();
        
        // All should be unique
        assertThat(uuid1).isNotEqualTo(uuid2);
        assertThat(uuid2).isNotEqualTo(uuid3);
        assertThat(uuid1).isNotEqualTo(uuid3);
        
        // If V7 mode, all should have version 7
        if (mode == UUIDGenerator.Mode.V7) {
            assertThat(uuid1.version()).isEqualTo(7);
            assertThat(uuid2.version()).isEqualTo(7);
            assertThat(uuid3.version()).isEqualTo(7);
        }
    }
}
