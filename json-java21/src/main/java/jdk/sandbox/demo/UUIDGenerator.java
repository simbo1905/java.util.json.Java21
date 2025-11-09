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
/// This is a backport of UUIDv7 from Java 26 (JDK-8334015: https://bugs.openjdk.org/browse/JDK-8334015)
/// 
/// UUIDv7's time-based sortability makes it an attractive option for globally unique identifiers,
/// especially in database applications. (https://www.rfc-editor.org/rfc/rfc9562#name-uuid-version-7)
/// 
/// As DBMS vendors add support for UUIDv7 (https://commitfest.postgresql.org/47/4388/) Java users
/// will not easily take advantage of its benefits until it is included in the Java core libraries.
/// 
/// Generation:
/// - ofEpochMillis: Creates a UUIDv7 from Unix Epoch timestamp (backport from Java 26)
/// - uniqueThenTime: User-ID-then-time-ordered 128-bit identifier
/// 
/// Formatting:
/// - formatAsDenseKey: Base62 encoded, 22 characters, zero-padded fixed-width
/// - Use UUID.toString() for standard RFC 4122 format (lowercase, 36 characters with dashes)
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

    /// Creates a type 7 UUID (UUIDv7) {@code UUID} from the given Unix Epoch timestamp.
    ///
    /// The returned {@code UUID} will have the given {@code timestamp} in
    /// the first 6 bytes, followed by the version and variant bits representing {@code UUIDv7},
    /// and the remaining bytes will contain random data from a cryptographically strong
    /// pseudo-random number generator.
    ///
    /// @apiNote {@code UUIDv7} values are created by allocating a Unix timestamp in milliseconds
    /// in the most significant 48 bits, allocating the required version (4 bits) and variant (2-bits)
    /// and filling the remaining 74 bits with random bits. As such, this method rejects {@code timestamp}
    /// values that do not fit into 48 bits.
    /// <p>
    /// Monotonicity (each subsequent value being greater than the last) is a primary characteristic
    /// of {@code UUIDv7} values. This is due to the {@code timestamp} value being part of the {@code UUID}.
    /// Callers of this method that wish to generate monotonic {@code UUIDv7} values are expected to
    /// ensure that the given {@code timestamp} value is monotonic.
    ///
    /// @param timestamp the number of milliseconds since midnight 1 Jan 1970 UTC,
    ///                 leap seconds excluded.
    ///
    /// @return a {@code UUID} constructed using the given {@code timestamp}
    ///
    /// @throws IllegalArgumentException if the timestamp is negative or greater than {@code (1L << 48) - 1}
    ///
    /// @since Backport from Java 26 (JDK-8334015)
    public static UUID ofEpochMillis(long timestamp) {
        if ((timestamp >> 48) != 0) {
            throw new IllegalArgumentException("Supplied timestamp: " + timestamp + " does not fit within 48 bits");
        }

        SecureRandom ng = getRandom();
        byte[] randomBytes = new byte[16];
        ng.nextBytes(randomBytes);

        // Embed the timestamp into the first 6 bytes
        randomBytes[0] = (byte)(timestamp >> 40);
        randomBytes[1] = (byte)(timestamp >> 32);
        randomBytes[2] = (byte)(timestamp >> 24);
        randomBytes[3] = (byte)(timestamp >> 16);
        randomBytes[4] = (byte)(timestamp >> 8);
        randomBytes[5] = (byte)(timestamp);

        // Set version to 7
        randomBytes[6] &= 0x0f;
        randomBytes[6] |= 0x70;

        // Set variant to IETF
        randomBytes[8] &= 0x3f;
        randomBytes[8] |= (byte) 0x80;

        // Convert byte array to UUID using ByteBuffer
        ByteBuffer buffer = ByteBuffer.wrap(randomBytes);
        long msb = buffer.getLong();
        long lsb = buffer.getLong();
        return new UUID(msb, lsb);
    }
    
    /// Convenience method to create a UUIDv7 from the current system time.
    /// Equivalent to {@code ofEpochMillis(System.currentTimeMillis())}.
    ///
    /// @return a {@code UUID} constructed using the current system time
    public static UUID timeThenRandom() {
        return ofEpochMillis(System.currentTimeMillis());
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
        // Test UUIDv7 with current time
        UUID uuid1 = UUIDGenerator.timeThenRandom();
        System.out.println("UUIDv7: " + uuid1);
        System.out.println("Dense: " + UUIDGenerator.formatAsDenseKey(uuid1));
        
        // Test UUIDv7 with specific timestamp
        UUID uuid2 = UUIDGenerator.ofEpochMillis(System.currentTimeMillis());
        System.out.println("UUIDv7 (explicit): " + uuid2);
        
        // Test uniqueThenTime
        UUID uuid3 = UUIDGenerator.uniqueThenTime(0x123456789ABCDEF0L);
        System.out.println("Unique UUID: " + uuid3);
        System.out.println("Unique Dense: " + UUIDGenerator.formatAsDenseKey(uuid3));
    }
}
