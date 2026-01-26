package jdk.sandbox.internal.util.json;

import java.util.function.Supplier;

/// Polyfill for JDK's LazyConstant using double-checked locking pattern
/// for thread-safe lazy initialization.
///
/// This provides a simpler API than StableValue:
/// - `LazyConstant.of(Supplier<T>)` - creates a lazy constant
/// - `.get()` - gets the value (computing if needed)
class LazyConstant<T> {
    private volatile T value;
    private final Supplier<T> supplier;
    private final Object lock = new Object();

    private LazyConstant(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    public static <T> LazyConstant<T> of(Supplier<T> supplier) {
        return new LazyConstant<>(supplier);
    }

    public T get() {
        T result = value;
        if (result == null) {
            synchronized (lock) {
                result = value;
                if (result == null) {
                    value = result = supplier.get();
                }
            }
        }
        return result;
    }
}
