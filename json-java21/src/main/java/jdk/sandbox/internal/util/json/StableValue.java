package jdk.sandbox.internal.util.json;

import java.util.function.Supplier;

/**
 * Mimics JDK's StableValue using double-checked locking pattern
 * for thread-safe lazy initialization.
 */
class StableValue<T> {
  private volatile T value;
  private final Object lock = new Object();

  private StableValue() {
  }

  public static <T> StableValue<T> of() {
    return new StableValue<>();
  }

  public T orElse(T defaultValue) {
    T result = value;
    return result != null ? result : defaultValue;
  }

  public T orElseSet(Supplier<T> supplier) {
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

  public void setOrThrow(T newValue) {
    if (value != null) {
      throw new IllegalStateException("Value already set");
    }
    synchronized (lock) {
      if (value != null) {
        throw new IllegalStateException("Value already set");
      }
      value = newValue;
    }
  }

  public static <T> Supplier<T> supplier(Supplier<T> supplier) {
    return new Supplier<>() {
      private volatile T cached;
      private final Object supplierLock = new Object();

      @Override
      public T get() {
        T result = cached;
        if (result == null) {
          synchronized (supplierLock) {
            result = cached;
            if (result == null) {
              cached = result = supplier.get();
            }
          }
        }
        return result;
      }
    };
  }
}