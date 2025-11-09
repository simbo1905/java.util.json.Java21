package jdk.sandbox.java.util.json;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.logging.Logger;

/// Provides UUID generation utilities supporting both UUIDv7 and alternative generation modes.
///
/// This class supports two UUID generation strategies:
/// 1. **UUIDv7** (default): Time-ordered UUIDs using Unix Epoch milliseconds (backported from Java 26)
/// 2. **Unique-Then-Time**: Custom format with unique MSB and time-based LSB
///
/// The generation mode can be configured via the system property {@code jdk.sandbox.uuid.generator.mode}
/// with values {@code "v7"} (default) or {@code "unique-then-time"}.
///
/// @since Backport from Java 26 (JDK-8334015)
public final class UUIDGenerator {
    
    private static final Logger LOGGER = Logger.getLogger(UUIDGenerator.class.getName());
    
    /// System property key for configuring UUID generation mode
    public static final String MODE_PROPERTY = "jdk.sandbox.uuid.generator.mode";
    
    /// Mode value for UUIDv7 generation
    public static final String MODE_V7 = "v7";
    
    /// Mode value for unique-then-time generation
    public static final String MODE_UNIQUE_THEN_TIME = "unique-then-time";
    
    /// Enum representing the UUID generation mode
    public enum Mode {
        /// UUIDv7 mode using Unix Epoch timestamp
        V7,
        /// Unique-then-time mode with custom format
        UNIQUE_THEN_TIME
    }
    
    /// Lazy initialization holder for SecureRandom
    private static final class LazyRandom {
        static final SecureRandom RANDOM = new SecureRandom();
    }
    
    private static final Mode DEFAULT_MODE = Mode.V7;
    private static final Mode CONFIGURED_MODE;
    
    static {
        final String propertyValue = System.getProperty(MODE_PROPERTY);
        Mode mode = DEFAULT_MODE;
        
        if (propertyValue != null) {
            final String normalized = propertyValue.trim().toLowerCase();
            mode = switch (normalized) {
                case MODE_V7 -> {
                    LOGGER.fine(() -> "UUID generator mode set to V7 via system property");
                    yield Mode.V7;
                }
                case MODE_UNIQUE_THEN_TIME -> {
                    LOGGER.fine(() -> "UUID generator mode set to UNIQUE_THEN_TIME via system property");
                    yield Mode.UNIQUE_THEN_TIME;
                }
                default -> {
                    LOGGER.warning(() -> "Invalid UUID generator mode: " + propertyValue + 
                                         ". Using default mode: " + DEFAULT_MODE);
                    yield DEFAULT_MODE;
                }
            };
        } else {
            LOGGER.fine(() -> "UUID generator mode not specified, using default: " + DEFAULT_MODE);
        }
        
        CONFIGURED_MODE = mode;
    }
    
    /// Private constructor to prevent instantiation
    private UUIDGenerator() {
        throw new AssertionError("UUIDGenerator cannot be instantiated");
    }
    
    /// Generates a UUID using the configured mode.
    ///
    /// The mode is determined by the system property {@code jdk.sandbox.uuid.generator.mode}.
    /// If not specified, defaults to UUIDv7 mode.
    ///
    /// @return a {@code UUID} generated according to the configured mode
    public static UUID generateUUID() {
        return switch (CONFIGURED_MODE) {
            case V7 -> ofEpochMillis(System.currentTimeMillis());
            case UNIQUE_THEN_TIME -> uniqueThenTime(generateUniqueMsb());
        };
    }
    
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
    public static UUID ofEpochMillis(final long timestamp) {
        if ((timestamp >> 48) != 0) {
            throw new IllegalArgumentException("Supplied timestamp: " + timestamp + " does not fit within 48 bits");
        }

        final byte[] randomBytes = new byte[16];
        LazyRandom.RANDOM.nextBytes(randomBytes);

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
        final ByteBuffer buffer = ByteBuffer.wrap(randomBytes);
        final long msb = buffer.getLong();
        final long lsb = buffer.getLong();
        return new UUID(msb, lsb);
    }
    
    /// Creates a UUID with unique MSB and time-based LSB.
    ///
    /// Format:
    /// ```
    /// ┌──────────────────────────────────────────────────────────────────────────────┐
    /// │  unique  (64 bits)  │  time+counter  (44 bits)  │  random  (20 bits)        │
    /// └──────────────────────────────────────────────────────────────────────────────┘
    /// ```
    ///
    /// The LSB contains:
    /// - 44 most significant bits: time counter for ordering
    /// - 20 least significant bits: random data
    ///
    /// @param uniqueMsb the unique 64-bit value for the MSB
    /// @return a {@code UUID} with the specified MSB and time-ordered LSB
    public static UUID uniqueThenTime(final long uniqueMsb) {
        final int timeBits = 44;
        final int randomBits = 20;
        final int randomMask = (1 << randomBits) - 1;
        final long timeCounter = timeCounterBits();
        final long msb = uniqueMsb;
        // Take the most significant 44 bits of timeCounter to preserve time ordering
        final long timeComponent = timeCounter >> (64 - timeBits); // timeBits is 44
        final long lsb = (timeComponent << randomBits) | (LazyRandom.RANDOM.nextInt() & randomMask);
        return new UUID(msb, lsb);
    }
    
    /// Generates a time-based counter value using current time and nano precision.
    ///
    /// Combines milliseconds since epoch with nano adjustment for higher precision ordering.
    ///
    /// @return a 64-bit time counter value
    private static long timeCounterBits() {
        final long currentTimeMillis = System.currentTimeMillis();
        final long nanoTime = System.nanoTime();
        // Combine milliseconds with nano adjustment for better ordering
        return (currentTimeMillis << 20) | (nanoTime & 0xFFFFF);
    }
    
    /// Generates a unique 64-bit MSB value using cryptographically strong random data.
    ///
    /// @return a unique 64-bit value
    private static long generateUniqueMsb() {
        return LazyRandom.RANDOM.nextLong();
    }
    
    /// Returns the currently configured UUID generation mode.
    ///
    /// @return the configured {@code Mode}
    public static Mode getConfiguredMode() {
        return CONFIGURED_MODE;
    }
}
