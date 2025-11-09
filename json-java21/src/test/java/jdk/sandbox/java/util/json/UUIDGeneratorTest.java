package jdk.sandbox.java.util.json;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.*;

/// Tests for {@link UUIDGenerator} covering UUIDv7 and unique-then-time generation modes.
class UUIDGeneratorTest {
    
    private static final Logger LOGGER = Logger.getLogger(UUIDGeneratorTest.class.getName());
    
    @Test
    @DisplayName("UUIDGenerator cannot be instantiated")
    void testCannotInstantiate() {
        LOGGER.info("Executing testCannotInstantiate");
        assertThatThrownBy(() -> {
            final var constructor = UUIDGenerator.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        }).isInstanceOf(Exception.class)
          .hasRootCauseInstanceOf(AssertionError.class)
          .hasRootCauseMessage("UUIDGenerator cannot be instantiated");
    }
    
    @Test
    @DisplayName("ofEpochMillis creates valid UUIDv7 with current timestamp")
    void testOfEpochMillisWithCurrentTime() {
        LOGGER.info("Executing testOfEpochMillisWithCurrentTime");
        final long currentTime = System.currentTimeMillis();
        final UUID uuid = UUIDGenerator.ofEpochMillis(currentTime);
        
        assertThat(uuid).isNotNull();
        assertThat(uuid.version()).isEqualTo(7);
        assertThat(uuid.variant()).isEqualTo(2); // IETF variant
    }
    
    @Test
    @DisplayName("ofEpochMillis creates valid UUIDv7 with zero timestamp")
    void testOfEpochMillisWithZero() {
        LOGGER.info("Executing testOfEpochMillisWithZero");
        final UUID uuid = UUIDGenerator.ofEpochMillis(0L);
        
        assertThat(uuid).isNotNull();
        assertThat(uuid.version()).isEqualTo(7);
        assertThat(uuid.variant()).isEqualTo(2);
    }
    
    @Test
    @DisplayName("ofEpochMillis creates valid UUIDv7 with max valid timestamp")
    void testOfEpochMillisWithMaxTimestamp() {
        LOGGER.info("Executing testOfEpochMillisWithMaxTimestamp");
        final long maxTimestamp = (1L << 48) - 1; // Max 48-bit value
        final UUID uuid = UUIDGenerator.ofEpochMillis(maxTimestamp);
        
        assertThat(uuid).isNotNull();
        assertThat(uuid.version()).isEqualTo(7);
        assertThat(uuid.variant()).isEqualTo(2);
    }
    
    @ParameterizedTest
    @ValueSource(longs = {-1L, -100L, Long.MIN_VALUE})
    @DisplayName("ofEpochMillis rejects negative timestamps")
    void testOfEpochMillisRejectsNegativeTimestamps(final long timestamp) {
        LOGGER.info("Executing testOfEpochMillisRejectsNegativeTimestamps with: " + timestamp);
        assertThatThrownBy(() -> UUIDGenerator.ofEpochMillis(timestamp))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not fit within 48 bits");
    }
    
    @Test
    @DisplayName("ofEpochMillis rejects timestamp exceeding 48 bits")
    void testOfEpochMillisRejectsOversizedTimestamp() {
        LOGGER.info("Executing testOfEpochMillisRejectsOversizedTimestamp");
        final long oversizedTimestamp = (1L << 48); // Just over 48 bits
        assertThatThrownBy(() -> UUIDGenerator.ofEpochMillis(oversizedTimestamp))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not fit within 48 bits");
    }
    
    @Test
    @DisplayName("ofEpochMillis produces unique UUIDs with same timestamp")
    void testOfEpochMillisUniquenessWithSameTimestamp() {
        LOGGER.info("Executing testOfEpochMillisUniquenessWithSameTimestamp");
        final long timestamp = System.currentTimeMillis();
        final Set<UUID> uuids = new HashSet<>();
        
        for (int i = 0; i < 1000; i++) {
            final UUID uuid = UUIDGenerator.ofEpochMillis(timestamp);
            assertThat(uuids.add(uuid))
                .as("UUID should be unique")
                .isTrue();
        }
    }
    
    @Test
    @DisplayName("ofEpochMillis produces monotonic UUIDs with increasing timestamps")
    void testOfEpochMillisMonotonicity() {
        LOGGER.info("Executing testOfEpochMillisMonotonicity");
        UUID previousUuid = null;
        
        for (long timestamp = 1000L; timestamp < 2000L; timestamp += 10) {
            final UUID uuid = UUIDGenerator.ofEpochMillis(timestamp);
            if (previousUuid != null) {
                assertThat(uuid.compareTo(previousUuid))
                    .as("UUID with later timestamp should be greater")
                    .isGreaterThan(0);
            }
            previousUuid = uuid;
        }
    }
    
    @Test
    @DisplayName("ofEpochMillis embeds timestamp correctly in first 48 bits")
    void testOfEpochMillisTimestampEmbedding() {
        LOGGER.info("Executing testOfEpochMillisTimestampEmbedding");
        final long timestamp = 0x123456789ABCL; // Known 48-bit value
        final UUID uuid = UUIDGenerator.ofEpochMillis(timestamp);
        
        final long msb = uuid.getMostSignificantBits();
        // Extract first 48 bits (before version/variant)
        final long extractedTimestamp = (msb >>> 16) & 0xFFFF_FFFF_FFFFL;
        
        assertThat(extractedTimestamp).isEqualTo(timestamp);
    }
    
    @Test
    @DisplayName("uniqueThenTime creates valid UUID")
    void testUniqueThenTime() {
        LOGGER.info("Executing testUniqueThenTime");
        final long uniqueMsb = 0x123456789ABCDEF0L;
        final UUID uuid = UUIDGenerator.uniqueThenTime(uniqueMsb);
        
        assertThat(uuid).isNotNull();
        assertThat(uuid.getMostSignificantBits()).isEqualTo(uniqueMsb);
    }
    
    @Test
    @DisplayName("uniqueThenTime produces unique UUIDs with same MSB")
    void testUniqueThenTimeUniqueness() {
        LOGGER.info("Executing testUniqueThenTimeUniqueness");
        final long uniqueMsb = 0x123456789ABCDEF0L;
        final Set<UUID> uuids = new HashSet<>();
        
        for (int i = 0; i < 1000; i++) {
            final UUID uuid = UUIDGenerator.uniqueThenTime(uniqueMsb);
            assertThat(uuids.add(uuid))
                .as("UUID should be unique even with same MSB")
                .isTrue();
        }
    }
    
    @Test
    @DisplayName("uniqueThenTime produces different UUIDs over time")
    void testUniqueThenTimeTemporalDifference() throws InterruptedException {
        LOGGER.info("Executing testUniqueThenTimeTemporalDifference");
        final long uniqueMsb = 0x123456789ABCDEF0L;
        final UUID uuid1 = UUIDGenerator.uniqueThenTime(uniqueMsb);
        
        // Small delay to ensure time progression
        Thread.sleep(2);
        
        final UUID uuid2 = UUIDGenerator.uniqueThenTime(uniqueMsb);
        
        assertThat(uuid1).isNotEqualTo(uuid2);
    }
    
    @Test
    @DisplayName("generateUUID produces valid UUIDs")
    void testGenerateUUID() {
        LOGGER.info("Executing testGenerateUUID");
        final Set<UUID> uuids = new HashSet<>();
        
        for (int i = 0; i < 100; i++) {
            final UUID uuid = UUIDGenerator.generateUUID();
            assertThat(uuid).isNotNull();
            assertThat(uuids.add(uuid))
                .as("Generated UUID should be unique")
                .isTrue();
        }
    }
    
    @Test
    @DisplayName("getConfiguredMode returns valid mode")
    void testGetConfiguredMode() {
        LOGGER.info("Executing testGetConfiguredMode");
        final UUIDGenerator.Mode mode = UUIDGenerator.getConfiguredMode();
        
        assertThat(mode)
            .isNotNull()
            .isIn(UUIDGenerator.Mode.V7, UUIDGenerator.Mode.UNIQUE_THEN_TIME);
    }
    
    @Test
    @DisplayName("Generated UUIDs have proper format")
    void testUUIDFormat() {
        LOGGER.info("Executing testUUIDFormat");
        final UUID uuid = UUIDGenerator.generateUUID();
        final String uuidString = uuid.toString();
        
        // UUID format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
        assertThat(uuidString)
            .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
    
    @Test
    @DisplayName("UUIDv7 generation is thread-safe")
    void testV7ThreadSafety() throws InterruptedException {
        LOGGER.info("Executing testV7ThreadSafety");
        final Set<UUID> uuids = new HashSet<>();
        final int threadCount = 10;
        final int uuidsPerThread = 100;
        final Thread[] threads = new Thread[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < uuidsPerThread; j++) {
                    final UUID uuid = UUIDGenerator.ofEpochMillis(System.currentTimeMillis());
                    synchronized (uuids) {
                        uuids.add(uuid);
                    }
                }
            });
            threads[i].start();
        }
        
        for (final Thread thread : threads) {
            thread.join();
        }
        
        assertThat(uuids).hasSize(threadCount * uuidsPerThread);
    }
    
    @Test
    @DisplayName("uniqueThenTime generation is thread-safe")
    void testUniqueThenTimeThreadSafety() throws InterruptedException {
        LOGGER.info("Executing testUniqueThenTimeThreadSafety");
        final Set<UUID> uuids = new HashSet<>();
        final int threadCount = 10;
        final int uuidsPerThread = 100;
        final Thread[] threads = new Thread[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            final long uniqueMsb = i; // Different MSB per thread
            threads[i] = new Thread(() -> {
                for (int j = 0; j < uuidsPerThread; j++) {
                    final UUID uuid = UUIDGenerator.uniqueThenTime(uniqueMsb);
                    synchronized (uuids) {
                        uuids.add(uuid);
                    }
                }
            });
            threads[i].start();
        }
        
        for (final Thread thread : threads) {
            thread.join();
        }
        
        assertThat(uuids).hasSize(threadCount * uuidsPerThread);
    }
}
