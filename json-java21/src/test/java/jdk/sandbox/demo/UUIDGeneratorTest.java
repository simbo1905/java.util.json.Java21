package jdk.sandbox.demo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class UUIDGeneratorTest {

    @Test
    void testTimeThenRandomGeneratesUUID() {
        UUID uuid = UUIDGenerator.timeThenRandom();
        assertNotNull(uuid);
    }

    @Test
    void testTimeThenRandomGeneratesUniqueUUIDs() {
        Set<UUID> uuids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            UUID uuid = UUIDGenerator.timeThenRandom();
            assertTrue(uuids.add(uuid), "Generated duplicate UUID: " + uuid);
        }
    }

    @Test
    void testTimeThenRandomIncreasingOrder() {
        // UUIDv7 uses millisecond timestamps in the first 48 bits.
        // Within the same millisecond, ordering is not guaranteed due to random bits.
        // This test verifies that UUIDs generated with different timestamps are ordered.
        UUID prev = UUIDGenerator.timeThenRandom();
        try {
            Thread.sleep(2); // Ensure at least 1ms passes
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        for (int i = 0; i < 10; i++) {
            UUID current = UUIDGenerator.timeThenRandom();
            // MSB should be increasing when timestamps differ
            assertTrue(current.getMostSignificantBits() >= prev.getMostSignificantBits(),
                "UUIDs with different timestamps should be time-ordered");
            prev = current;
            try {
                Thread.sleep(1); // Small delay to ensure different timestamps
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    void testUniqueThenTimeGeneratesUUID() {
        long uniqueMsb = 0x123456789ABCDEF0L;
        UUID uuid = UUIDGenerator.uniqueThenTime(uniqueMsb);
        assertNotNull(uuid);
        assertEquals(uniqueMsb, uuid.getMostSignificantBits());
    }

    @Test
    void testUniqueThenTimeWithDifferentUserIds() {
        long userId1 = 1L;
        long userId2 = 2L;
        
        UUID uuid1 = UUIDGenerator.uniqueThenTime(userId1);
        UUID uuid2 = UUIDGenerator.uniqueThenTime(userId2);
        
        assertNotEquals(uuid1, uuid2);
        assertEquals(userId1, uuid1.getMostSignificantBits());
        assertEquals(userId2, uuid2.getMostSignificantBits());
    }

    @Test
    void testFormatAsDenseKeyLength() {
        UUID uuid = UUIDGenerator.timeThenRandom();
        String denseKey = UUIDGenerator.formatAsDenseKey(uuid);
        
        assertEquals(22, denseKey.length(), "Dense key should be 22 characters");
    }

    @Test
    void testFormatAsDenseKeyAlphanumeric() {
        UUID uuid = UUIDGenerator.timeThenRandom();
        String denseKey = UUIDGenerator.formatAsDenseKey(uuid);
        
        assertTrue(denseKey.matches("[0-9A-Za-z]+"),
            "Dense key should only contain alphanumeric characters");
    }

    @Test
    void testFormatAsDenseKeyUniqueness() {
        Set<String> denseKeys = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            UUID uuid = UUIDGenerator.timeThenRandom();
            String denseKey = UUIDGenerator.formatAsDenseKey(uuid);
            assertTrue(denseKeys.add(denseKey), "Generated duplicate dense key: " + denseKey);
        }
    }

    @Test
    void testFormatAsDenseKeyZeroPadding() {
        // Test with UUID that has many leading zeros
        UUID uuid = new UUID(0L, 1L);
        String denseKey = UUIDGenerator.formatAsDenseKey(uuid);
        
        assertEquals(22, denseKey.length(), "Dense key should be zero-padded to 22 characters");
    }

    @Test
    void testFormatAsDenseKeyLexicographicOrdering() {
        // Time-ordered UUIDs should produce lexicographically ordered dense keys
        UUID uuid1 = UUIDGenerator.timeThenRandom();
        String key1 = UUIDGenerator.formatAsDenseKey(uuid1);
        
        // Wait a bit to ensure time advances
        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        UUID uuid2 = UUIDGenerator.timeThenRandom();
        String key2 = UUIDGenerator.formatAsDenseKey(uuid2);
        
        assertTrue(key1.compareTo(key2) <= 0,
            "Dense keys should maintain lexicographic ordering for time-ordered UUIDs");
    }

    @Test
    void testLazyRandomInitialization() {
        // This test verifies that the LazyRandom pattern works correctly
        // by generating multiple UUIDs and ensuring they are all different
        Set<UUID> uuids = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            UUID uuid = UUIDGenerator.timeThenRandom();
            assertTrue(uuids.add(uuid), "LazyRandom should generate unique random values");
        }
    }

    @Test
    void testThreadSafetyOfLazyRandom() throws InterruptedException {
        final int threadCount = 10;
        final int uuidsPerThread = 100;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(threadCount);
        final Set<UUID> allUuids = ConcurrentHashMap.newKeySet();
        final AtomicInteger duplicateCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    for (int j = 0; j < uuidsPerThread; j++) {
                        UUID uuid = UUIDGenerator.timeThenRandom();
                        if (!allUuids.add(uuid)) {
                            duplicateCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown(); // Start all threads
        doneLatch.await(); // Wait for all threads to complete

        assertEquals(0, duplicateCount.get(), "Should not generate duplicate UUIDs in multi-threaded scenario");
        assertEquals(threadCount * uuidsPerThread, allUuids.size());
    }

    @RepeatedTest(10)
    void testSequenceCounterWraparound() {
        // Generate many UUIDs quickly to test sequence counter
        Set<UUID> uuids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            UUID uuid = UUIDGenerator.timeThenRandom();
            assertTrue(uuids.add(uuid), "Should generate unique UUIDs even with sequence counter");
        }
    }

    @Test
    void testTimeCounterBitsFormat() {
        // Test that timeCounterBits produces reasonable values
        long bits1 = UUIDGenerator.timeCounterBits();
        long bits2 = UUIDGenerator.timeCounterBits();
        
        // Should be increasing or equal (if same millisecond)
        assertTrue(bits2 >= bits1, "Time counter bits should be non-decreasing");
    }

    @Test
    void testUniqueThenTimePreservesUniqueBits() {
        long uniqueMsb = 0xFFFFFFFFFFFFFFFL;
        UUID uuid = UUIDGenerator.uniqueThenTime(uniqueMsb);
        
        assertEquals(uniqueMsb, uuid.getMostSignificantBits(),
            "uniqueThenTime should preserve the unique MSB");
    }

    @Test
    void testFormatAsDenseKeyDeterministic() {
        UUID uuid = new UUID(0x123456789ABCDEF0L, 0xFEDCBA9876543210L);
        String key1 = UUIDGenerator.formatAsDenseKey(uuid);
        String key2 = UUIDGenerator.formatAsDenseKey(uuid);
        
        assertEquals(key1, key2, "Same UUID should produce same dense key");
    }

    @Test
    void testOfEpochMillisVersion7() {
        // Test that generated UUID has version 7
        long timestamp = System.currentTimeMillis();
        UUID uuid = UUIDGenerator.ofEpochMillis(timestamp);
        
        // Extract version bits (bits 48-51 of the UUID)
        long msb = uuid.getMostSignificantBits();
        int version = (int)((msb >> 12) & 0x0F);
        
        assertEquals(7, version, "UUID should have version 7");
    }

    @Test
    void testOfEpochMillisVariantIETF() {
        // Test that generated UUID has IETF variant
        long timestamp = System.currentTimeMillis();
        UUID uuid = UUIDGenerator.ofEpochMillis(timestamp);
        
        // Extract variant bits (bits 64-65 of the UUID)
        long lsb = uuid.getLeastSignificantBits();
        int variant = (int)((lsb >> 62) & 0x03);
        
        assertEquals(2, variant, "UUID should have IETF variant (2)");
    }

    @Test
    void testOfEpochMillisTimestampExtraction() {
        // Test that timestamp can be extracted from UUID
        long timestamp = 1000000000000L;
        UUID uuid = UUIDGenerator.ofEpochMillis(timestamp);
        
        // Extract timestamp from first 48 bits
        long msb = uuid.getMostSignificantBits();
        long extractedTimestamp = msb >>> 16; // Shift right 16 bits to get top 48 bits
        
        assertEquals(timestamp, extractedTimestamp, "Timestamp should be embedded in UUID");
    }

    @Test
    void testOfEpochMillisMonotonicity() {
        // Test that UUIDs with increasing timestamps are monotonic
        UUID uuid1 = UUIDGenerator.ofEpochMillis(1000000000000L);
        UUID uuid2 = UUIDGenerator.ofEpochMillis(1000000000001L);
        UUID uuid3 = UUIDGenerator.ofEpochMillis(1000000000002L);
        
        assertTrue(uuid2.compareTo(uuid1) > 0, "UUID2 should be greater than UUID1");
        assertTrue(uuid3.compareTo(uuid2) > 0, "UUID3 should be greater than UUID2");
    }

    @Test
    void testOfEpochMillisInvalidTimestamp() {
        // Test that timestamps that don't fit in 48 bits are rejected
        long invalidTimestamp = (1L << 48); // 2^48, doesn't fit in 48 bits
        
        assertThrows(IllegalArgumentException.class, () -> {
            UUIDGenerator.ofEpochMillis(invalidTimestamp);
        }, "Should throw IllegalArgumentException for timestamp that doesn't fit in 48 bits");
    }

    @Test
    void testOfEpochMillisNegativeTimestamp() {
        // Test that negative timestamps are rejected
        long negativeTimestamp = -1L;
        
        assertThrows(IllegalArgumentException.class, () -> {
            UUIDGenerator.ofEpochMillis(negativeTimestamp);
        }, "Should throw IllegalArgumentException for negative timestamp");
    }

    @Test
    void testOfEpochMillisUniqueness() {
        // Test that multiple UUIDs with same timestamp are still unique (due to random bits)
        long timestamp = System.currentTimeMillis();
        Set<UUID> uuids = new HashSet<>();
        
        for (int i = 0; i < 100; i++) {
            UUID uuid = UUIDGenerator.ofEpochMillis(timestamp);
            assertTrue(uuids.add(uuid), "Each UUID should be unique even with same timestamp");
        }
    }
}
