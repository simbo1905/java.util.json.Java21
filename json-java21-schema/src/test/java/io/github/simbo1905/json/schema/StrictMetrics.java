package io.github.simbo1905.json.schema;

import java.util.concurrent.ConcurrentHashMap;

/// Thread-safe metrics container for the JSON Schema Test Suite run.
/// Thread-safe strict metrics container for the JSON Schema Test Suite run
final class StrictMetrics {
  final java.util.concurrent.atomic.LongAdder total = new java.util.concurrent.atomic.LongAdder();
  final java.util.concurrent.atomic.LongAdder run = new java.util.concurrent.atomic.LongAdder();
  final java.util.concurrent.atomic.LongAdder passed = new java.util.concurrent.atomic.LongAdder();
  final java.util.concurrent.atomic.LongAdder failed = new java.util.concurrent.atomic.LongAdder();
  final java.util.concurrent.atomic.LongAdder skippedUnsupported = new java.util.concurrent.atomic.LongAdder();
  final java.util.concurrent.atomic.LongAdder skippedMismatch = new java.util.concurrent.atomic.LongAdder();

  // Legacy counters for backward compatibility
  final java.util.concurrent.atomic.LongAdder groupsDiscovered = new java.util.concurrent.atomic.LongAdder();
  final java.util.concurrent.atomic.LongAdder testsDiscovered = new java.util.concurrent.atomic.LongAdder();
  final java.util.concurrent.atomic.LongAdder skipTestException = new java.util.concurrent.atomic.LongAdder();

  final ConcurrentHashMap<String, FileCounters> perFile = new ConcurrentHashMap<>();

  /// Per-file counters for detailed metrics
  static final class FileCounters {
    final java.util.concurrent.atomic.LongAdder groups = new java.util.concurrent.atomic.LongAdder();
    final java.util.concurrent.atomic.LongAdder tests = new java.util.concurrent.atomic.LongAdder();
    final java.util.concurrent.atomic.LongAdder run = new java.util.concurrent.atomic.LongAdder();
    final java.util.concurrent.atomic.LongAdder pass = new java.util.concurrent.atomic.LongAdder();
    final java.util.concurrent.atomic.LongAdder fail = new java.util.concurrent.atomic.LongAdder();
    final java.util.concurrent.atomic.LongAdder skipUnsupported = new java.util.concurrent.atomic.LongAdder();
    final java.util.concurrent.atomic.LongAdder skipException = new java.util.concurrent.atomic.LongAdder();
    final java.util.concurrent.atomic.LongAdder skipMismatch = new java.util.concurrent.atomic.LongAdder();
  }
}
