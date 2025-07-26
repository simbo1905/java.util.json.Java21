package io.github.simbo1905.tracker;

import jdk.sandbox.java.util.json.Json;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/// Command-line runner for the API Tracker
/// 
/// Usage: java io.github.simbo1905.tracker.ApiTrackerRunner [loglevel]
/// where loglevel is one of: SEVERE, WARNING, INFO, FINE, FINER, FINEST
public class ApiTrackerRunner {
    
    public static void main(String[] args) {
        // Configure logging based on command line argument
        final var logLevel = args.length > 0 ? Level.parse(args[0].toUpperCase()) : Level.INFO;
        configureLogging(logLevel);
        
        System.out.println("=== JSON API Tracker ===");
        System.out.println("Comparing local jdk.sandbox.java.util.json with upstream java.util.json");
        System.out.println("Log level: " + logLevel);
        System.out.println();
        
        try {
            // Run the full comparison
            final var report = ApiTracker.runFullComparison();
            
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