package io.github.simbo1905.tracker;

import jdk.sandbox.java.util.json.Json;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/// Command-line runner for the API Tracker
/// 
/// Usage: java io.github.simbo1905.tracker.ApiTrackerRunner [loglevel] [mode] [sourcepath]
/// 
/// Arguments:
/// - loglevel: SEVERE, WARNING, INFO, FINE, FINER, FINEST (default: INFO)
/// - mode: binary|source (default: binary)
///   - binary: Compare binary reflection (local) vs source parsing (remote)
///   - source: Compare source parsing (local) vs source parsing (remote) for accurate parameter names
/// - sourcepath: Path to local source files (required for source mode)
public class ApiTrackerRunner {
    
    public static void main(String[] args) {
        // Parse command line arguments
        final var logLevel = args.length > 0 ? Level.parse(args[0].toUpperCase()) : Level.INFO;
        final var mode = args.length > 1 ? args[1].toLowerCase() : "binary";
        final var sourcePath = args.length > 2 ? args[2] : null;
        
        configureLogging(logLevel);
        
        System.out.println("=== JSON API Tracker ===");
        System.out.println("Comparing local jdk.sandbox.java.util.json with upstream java.util.json");
        System.out.println("Log level: " + logLevel);
        System.out.println("Mode: " + mode);
        if (sourcePath != null) {
            System.out.println("Local source path: " + sourcePath);
        }
        System.out.println();
        
        try {
            // Run comparison based on mode
            final var report = switch (mode) {
                case "binary" -> {
                    System.out.println("Running binary reflection vs source parsing comparison");
                    yield ApiTracker.runFullComparison();
                }
                case "source" -> {
                    if (sourcePath == null) {
                        System.err.println("Error: source mode requires sourcepath argument");
                        System.exit(1);
                    }
                    System.out.println("Running source-to-source comparison (apples-to-apples)");
                    yield ApiTracker.runSourceToSourceComparison(sourcePath);
                }
                default -> {
                    System.err.println("Error: mode must be 'binary' or 'source'");
                    System.exit(1);
                    yield null; // Never reached
                }
            };
            
            // Pretty print the report
            System.out.println("=== Comparison Report ===");
            System.out.println(Json.toDisplayString(report, 2));
            
        } catch (Exception e) {
            System.err.println("Error during comparison: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void configureLogging(Level level) {
        // Get root logger
        final var rootLogger = Logger.getLogger("");
        rootLogger.setLevel(level);
        
        // Configure console handler
        for (var handler : rootLogger.getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                handler.setLevel(level);
            }
        }
    }
}