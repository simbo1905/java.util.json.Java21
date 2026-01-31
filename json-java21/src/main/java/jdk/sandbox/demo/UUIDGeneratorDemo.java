package jdk.sandbox.demo;

import jdk.sandbox.java.util.json.UUIDGenerator;

import java.util.UUID;

/// Demonstrates usage of {@link UUIDGenerator} with both UUIDv7 and unique-then-time modes.
///
/// This demo shows:
/// - Default UUIDv7 generation
/// - Direct UUIDv7 creation with specific timestamps
/// - Unique-then-time UUID generation
/// - Configuration mode detection
public final class UUIDGeneratorDemo {
    
    public static void main(final String[] args) {
        System.out.println("=== UUID Generator Demo ===\n");
        
        // Show current configuration
        final UUIDGenerator.Mode mode = UUIDGenerator.getConfiguredMode();
        System.out.println("Configured mode: " + mode);
        System.out.println("System property: " + System.getProperty(UUIDGenerator.MODE_PROPERTY, "(not set)"));
        System.out.println();
        
        // Generate UUIDs using the configured mode
        System.out.println("--- Generating UUIDs with configured mode ---");
        for (int i = 0; i < 5; i++) {
            final UUID uuid = UUIDGenerator.generateUUID();
            System.out.println("UUID " + (i + 1) + ": " + uuid);
            if (mode == UUIDGenerator.Mode.V7) {
                System.out.println("  Version: " + uuid.version() + ", Variant: " + uuid.variant());
            }
        }
        System.out.println();
        
        // Demonstrate UUIDv7 with specific timestamps
        System.out.println("--- UUIDv7 with specific timestamps ---");
        final long baseTime = System.currentTimeMillis();
        for (int i = 0; i < 3; i++) {
            final long timestamp = baseTime + (i * 1000); // 1 second apart
            final UUID uuid = UUIDGenerator.ofEpochMillis(timestamp);
            System.out.println("Timestamp: " + timestamp + " -> " + uuid);
            System.out.println("  Version: " + uuid.version() + ", Variant: " + uuid.variant());
        }
        System.out.println();
        
        // Demonstrate unique-then-time mode
        System.out.println("--- Unique-then-time mode ---");
        for (int i = 0; i < 3; i++) {
            final long uniqueMsb = 0x1000000000000000L + i;
            final UUID uuid = UUIDGenerator.uniqueThenTime(uniqueMsb);
            System.out.println("Unique MSB: " + Long.toHexString(uniqueMsb) + " -> " + uuid);
        }
        System.out.println();
        
        // Demonstrate monotonicity of UUIDv7
        System.out.println("--- UUIDv7 Monotonicity (time-ordered) ---");
        UUID previous = null;
        for (int i = 0; i < 5; i++) {
            final long timestamp = baseTime + (i * 100); // 100ms apart
            final UUID current = UUIDGenerator.ofEpochMillis(timestamp);
            if (previous != null) {
                final int comparison = current.compareTo(previous);
                System.out.println(current + " > " + previous + " ? " + (comparison > 0));
            } else {
                System.out.println(current + " (first)");
            }
            previous = current;
        }
        System.out.println();
        
        // Show configuration examples
        System.out.println("=== Configuration Examples ===");
        System.out.println("To use UUIDv7 (default):");
        System.out.println("  java -jar app.jar");
        System.out.println("  or");
        System.out.println("  java -D" + UUIDGenerator.MODE_PROPERTY + "=v7 -jar app.jar");
        System.out.println();
        System.out.println("To use unique-then-time mode:");
        System.out.println("  java -D" + UUIDGenerator.MODE_PROPERTY + "=unique-then-time -jar app.jar");
        System.out.println();
        System.out.println("On Android, set in Application.onCreate():");
        System.out.println("  System.setProperty(\"" + UUIDGenerator.MODE_PROPERTY + "\", \"v7\");");
        System.out.println("  Note: Must be set before first UUIDGenerator access");
    }
}
