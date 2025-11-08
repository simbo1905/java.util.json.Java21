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
        UUID prev = UUIDGenerator.timeThenRandom();
        for (int i = 0; i < 100; i++) {
            UUID current = UUIDGenerator.timeThenRandom();
            // MSB should be increasing (time+counter)
            assertTrue(current.getMostSignificantBits() >= prev.getMostSignificantBits(),
                "UUIDs should be time-ordered");
            prev = current;
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
    void testFormatAsUUIDLowercase() {
        UUID uuid = new UUID(0x123456789ABCDEF0L, 0xFEDCBA9876543210L);
        String formatted = UUIDGenerator.formatAsUUID(uuid);
        
        assertEquals(36, formatted.length());
        assertTrue(formatted.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
            "Should match UUID format: " + formatted);
        assertEquals("12345678-9abc-def0-fedc-ba9876543210", formatted);
    }

    @Test
    void testFormatAsUUIDUppercase() {
        UUID uuid = new UUID(0x123456789ABCDEF0L, 0xFEDCBA9876543210L);
        String formatted = UUIDGenerator.formatAsUUID(uuid, true);
        
        assertEquals(36, formatted.length());
        assertTrue(formatted.matches("[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}"),
            "Should match uppercase UUID format: " + formatted);
        assertEquals("12345678-9ABC-DEF0-FEDC-BA9876543210", formatted);
    }

    @Test
    void testFormatAsUUIDDefaultIsLowercase() {
        UUID uuid = new UUID(0x123456789ABCDEF0L, 0xFEDCBA9876543210L);
        String formatted = UUIDGenerator.formatAsUUID(uuid);
        String formattedExplicit = UUIDGenerator.formatAsUUID(uuid, false);
        
        assertEquals(formatted, formattedExplicit);
        assertEquals(formatted, formatted.toLowerCase());
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
}
