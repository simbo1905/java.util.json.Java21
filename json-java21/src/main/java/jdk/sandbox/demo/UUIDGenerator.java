// SPDX-FileCopyrightText: 2025 Simon Massey
// SPDX-License-Identifier: MIT

package jdk.sandbox.demo;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;

/// UUID Generator providing time-ordered globally unique IDs.
/// 
/// Generation:
/// - timeThenRandom: Time-ordered globally unique 128-bit identifier
/// - uniqueThenTime: User-ID-then-time-ordered 128-bit identifier
/// 
/// Formatting:
/// - formatAsUUID: RFC 4122 format with dashes, 36 characters, uppercase or lowercase
/// - formatAsDenseKey: Base62 encoded, 22 characters, zero-padded fixed-width
/// 
/// Note: Intended usage is one instance per JVM process. Multiple instances
/// in the same process do not guarantee uniqueness due to shared sequence counter.
/// 
/// Note: 22-character keys are larger than Firebase push IDs (20 characters)
/// but provide full 128-bit time-ordered randomized identifiers.
public class UUIDGenerator {
    static final AtomicInteger sequence = new AtomicInteger(0);
    
    /// LazyRandom holder using double-checked locking pattern for thread-safe lazy initialization
    private static volatile SecureRandom random;
    private static final Object randomLock = new Object();
    
    static SecureRandom getRandom() {
        SecureRandom result = random;
        if (result == null) {
            synchronized (randomLock) {
                result = random;
                if (result == null) {
                    random = result = new SecureRandom();
                }
            }
        }
        return result;
    }

    static long timeCounterBits() {
        long ms = System.currentTimeMillis();
        int seq = sequence.incrementAndGet() & 0xFFFFF; // 20-bit counter
        return (ms << 20) | seq;
    }

    // Generation - Public API

    /// ┌──────────────────────────────────────────────────────────────────────────────┐
    /// │  time+counter  (64 bits)  │  random  (64 bits)                              │
    /// └──────────────────────────────────────────────────────────────────────────────┘
    public static UUID timeThenRandom() {
        long msb = timeCounterBits();
        long lsb = getRandom().nextLong();
        return new UUID(msb, lsb);
    }

    /// ┌──────────────────────────────────────────────────────────────────────────────┐
    /// │  unique  (64 bits)  │  time+counter  (44 bits)  │  random  (20 bits)        │
    /// └──────────────────────────────────────────────────────────────────────────────┘
    public static UUID uniqueThenTime(long uniqueMsb) {
        final int timeBits = 44;
        final int randomBits = 20;
        final int randomMask = (1 << randomBits) - 1;
        long timeCounter = timeCounterBits();
        long msb = uniqueMsb;
        // Take the most significant 44 bits of timeCounter to preserve time ordering
        long timeComponent = timeCounter >> (64 - timeBits); // timeBits is 44
        long lsb = (timeComponent << randomBits) | (getRandom().nextInt() & randomMask);
        return new UUID(msb, lsb);
    }

    // Formatting - Public API

    /// Standard RFC 4122 UUID layout with dashes.
    /// Returns fixed-length 36 character string: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
    public static String formatAsUUID(UUID uuid, boolean uppercase) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        
        StringBuilder hex = new StringBuilder();
        for (byte b : buffer.array()) {
            hex.append(String.format("%02x", b));
        }
        
        String hexStr = hex.toString();
        String formatted = String.format("%s-%s-%s-%s-%s",
            hexStr.substring(0, 8),
            hexStr.substring(8, 12),
            hexStr.substring(12, 16),
            hexStr.substring(16, 20),
            hexStr.substring(20));
        
        return uppercase ? formatted.toUpperCase() : formatted.toLowerCase();
    }

    public static String formatAsUUID(UUID uuid) {
        return formatAsUUID(uuid, false);
    }

    /// Alphanumeric base62 encoding.
    /// Returns fixed-length 22 character string for lexicographic sorting.
    public static String formatAsDenseKey(UUID uuid) {
        final String alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        final int base = alphabet.length();
        final int expectedLength = 22;
        
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        
        BigInteger value = new BigInteger(1, buffer.array());
        StringBuilder sb = new StringBuilder();
        
        while (value.compareTo(BigInteger.ZERO) > 0) {
            BigInteger[] divMod = value.divideAndRemainder(BigInteger.valueOf(base));
            sb.append(alphabet.charAt(divMod[1].intValue()));
            value = divMod[0];
        }
        
        String encoded = sb.reverse().toString();
        while (encoded.length() < expectedLength) {
            encoded = "0" + encoded;
        }
        
        return encoded;
    }

    public static void main(String[] args) {
        UUID uuid1 = UUIDGenerator.timeThenRandom();
        System.out.println("UUID: " + UUIDGenerator.formatAsUUID(uuid1));
        System.out.println("Dense: " + UUIDGenerator.formatAsDenseKey(uuid1));
        
        UUID uuid2 = UUIDGenerator.uniqueThenTime(0x123456789ABCDEF0L);
        System.out.println("Unique UUID: " + UUIDGenerator.formatAsUUID(uuid2, true));
        System.out.println("Unique Dense: " + UUIDGenerator.formatAsDenseKey(uuid2));
    }
}
