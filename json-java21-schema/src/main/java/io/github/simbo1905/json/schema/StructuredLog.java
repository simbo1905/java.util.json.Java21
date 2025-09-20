package io.github.simbo1905.json.schema;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/// Package-private helper for structured JUL logging with simple sampling.
/// Produces concise key=value pairs prefixed by event=NAME.
final class StructuredLog {
    private static final Map<String, AtomicLong> COUNTERS = new ConcurrentHashMap<>();

    static void fine(Logger log, String event, Object... kv) {
        if (log.isLoggable(Level.FINE)) log.fine(() -> ev(event, kv));
    }

    static void finer(Logger log, String event, Object... kv) {
        if (log.isLoggable(Level.FINER)) log.finer(() -> ev(event, kv));
    }

    static void finest(Logger log, String event, Object... kv) {
        if (log.isLoggable(Level.FINEST)) log.finest(() -> ev(event, kv));
    }

    /// Log at FINEST but only every Nth occurrence per event key.
    static void finestSampled(Logger log, String event, int everyN, Object... kv) {
        if (!log.isLoggable(Level.FINEST)) return;
        if (everyN <= 1) {
            log.finest(() -> ev(event, kv));
            return;
        }
        long n = COUNTERS.computeIfAbsent(event, k -> new AtomicLong()).incrementAndGet();
        if (n % everyN == 0L) {
            log.finest(() -> ev(event, kv("sample", n, kv)));
        }
    }

    private static Object[] kv(String k, Object v, Object... rest) {
        Object[] out = new Object[2 + rest.length];
        out[0] = k; out[1] = v;
        System.arraycopy(rest, 0, out, 2, rest.length);
        return out;
    }

    static String ev(String event, Object... kv) {
        StringBuilder sb = new StringBuilder(64);
        sb.append("event=").append(sanitize(event));
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object key = kv[i];
            Object val = kv[i + 1];
            if (key == null) continue;
            String k = key.toString();
            String v = val == null ? "null" : sanitize(val.toString());
            sb.append(' ').append(k).append('=');
            // quote if contains whitespace
            if (needsQuotes(v)) sb.append('"').append(v).append('"'); else sb.append(v);
        }
        return sb.toString();
    }

    private static boolean needsQuotes(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) return true;
            if (c == '"') return true;
        }
        return false;
    }

    private static String sanitize(String s) {
        if (s == null) return "null";
        // Trim overly long payloads to keep logs readable
        final int MAX = 256;
        String trimmed = s.length() > MAX ? s.substring(0, MAX) + "…" : s;
        // Collapse newlines and tabs
        return trimmed.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
    }
}

