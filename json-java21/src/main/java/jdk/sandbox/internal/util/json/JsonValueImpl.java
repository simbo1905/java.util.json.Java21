package jdk.sandbox.internal.util.json;

// Minimal internal marker for backport compatibility
interface JsonValueImpl {
    char[] doc();
    int offset();
}

