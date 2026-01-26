package jdk.sandbox.internal.util.json;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests for [LazyConstant] polyfill that provides thread-safe lazy initialization.
class LazyConstantTest {

    private static final Logger LOG = Logger.getLogger(LazyConstantTest.class.getName());

    @Test
    void testLazyInitialization() {
        LOG.info("Running testLazyInitialization");
        
        AtomicInteger computeCount = new AtomicInteger(0);
        
        LazyConstant<String> lazy = LazyConstant.of(() -> {
            computeCount.incrementAndGet();
            return "computed value";
        });
        
        // Supplier should not be called yet
        assertThat(computeCount.get()).isEqualTo(0);
        
        // First get() should compute
        String value = lazy.get();
        assertThat(value).isEqualTo("computed value");
        assertThat(computeCount.get()).isEqualTo(1);
        
        // Second get() should return cached value without recomputing
        String value2 = lazy.get();
        assertThat(value2).isEqualTo("computed value");
        assertThat(computeCount.get()).isEqualTo(1);
    }

    @Test
    void testReturnsComputedValue() {
        LOG.info("Running testReturnsComputedValue");
        
        LazyConstant<Integer> lazy = LazyConstant.of(() -> 42);
        
        assertThat(lazy.get()).isEqualTo(42);
        assertThat(lazy.get()).isEqualTo(42);
    }

    @Test
    void testWithComplexObject() {
        LOG.info("Running testWithComplexObject");
        
        LazyConstant<StringBuilder> lazy = LazyConstant.of(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("hello");
            sb.append(" ");
            sb.append("world");
            return sb;
        });
        
        StringBuilder result = lazy.get();
        assertThat(result.toString()).isEqualTo("hello world");
        
        // Should return the same instance
        assertThat(lazy.get()).isSameAs(result);
    }

    @Test
    void testThreadSafety() throws InterruptedException {
        LOG.info("Running testThreadSafety");
        
        AtomicInteger computeCount = new AtomicInteger(0);
        
        LazyConstant<String> lazy = LazyConstant.of(() -> {
            computeCount.incrementAndGet();
            // Simulate some computation time
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "thread-safe value";
        });
        
        int numThreads = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    String value = lazy.get();
                    assertThat(value).isEqualTo("thread-safe value");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        
        // Release all threads at once
        startLatch.countDown();
        
        // Wait for all threads to complete
        doneLatch.await();
        executor.shutdown();
        
        // Supplier should only have been called once despite concurrent access
        assertThat(computeCount.get()).isEqualTo(1);
    }

    @Test
    void testMultipleInstances() {
        LOG.info("Running testMultipleInstances");
        
        AtomicInteger counter1 = new AtomicInteger(0);
        AtomicInteger counter2 = new AtomicInteger(0);
        
        LazyConstant<String> lazy1 = LazyConstant.of(() -> {
            counter1.incrementAndGet();
            return "value1";
        });
        
        LazyConstant<String> lazy2 = LazyConstant.of(() -> {
            counter2.incrementAndGet();
            return "value2";
        });
        
        assertThat(lazy1.get()).isEqualTo("value1");
        assertThat(lazy2.get()).isEqualTo("value2");
        
        assertThat(counter1.get()).isEqualTo(1);
        assertThat(counter2.get()).isEqualTo(1);
    }

    @Test
    void testSupplierExceptionPropagates() {
        LOG.info("Running testSupplierExceptionPropagates");
        
        LazyConstant<String> lazy = LazyConstant.of(() -> {
            throw new RuntimeException("computation failed");
        });
        
        try {
            lazy.get();
            assertThat(false).as("Should have thrown exception").isTrue();
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).isEqualTo("computation failed");
        }
    }

    @Test
    void testWithExpensiveComputation() {
        LOG.info("Running testWithExpensiveComputation");
        
        AtomicInteger computeCount = new AtomicInteger(0);
        
        // Simulates expensive computation like parsing a large string
        LazyConstant<String> lazy = LazyConstant.of(() -> {
            computeCount.incrementAndGet();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                sb.append(i).append(",");
            }
            return sb.toString();
        });
        
        // Access multiple times
        for (int i = 0; i < 100; i++) {
            String value = lazy.get();
            assertThat(value).startsWith("0,1,2,");
        }
        
        // Should only compute once
        assertThat(computeCount.get()).isEqualTo(1);
    }

    @Test
    void testCachesValueAcrossMultipleGets() {
        LOG.info("Running testCachesValueAcrossMultipleGets");
        
        AtomicInteger callCount = new AtomicInteger(0);
        
        LazyConstant<Object> lazy = LazyConstant.of(() -> {
            callCount.incrementAndGet();
            return new Object(); // Each call would create a new instance
        });
        
        Object first = lazy.get();
        Object second = lazy.get();
        Object third = lazy.get();
        
        // All should be the same instance
        assertThat(first).isSameAs(second);
        assertThat(second).isSameAs(third);
        
        // Supplier called only once
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void testUsedInJsonParsingContext() {
        LOG.info("Running testUsedInJsonParsingContext");
        
        // Simulates how LazyConstant is used in JsonStringImpl/JsonNumberImpl
        // where the string representation is computed lazily from a char array
        
        char[] doc = "\"hello world\"".toCharArray();
        int start = 0;
        int end = doc.length;
        
        LazyConstant<String> lazyString = LazyConstant.of(() -> 
            new String(doc, start, end - start)
        );
        
        assertThat(lazyString.get()).isEqualTo("\"hello world\"");
        assertThat(lazyString.get()).isEqualTo("\"hello world\"");
    }

    @Test
    void testMemoizesNullUnsupported() {
        LOG.info("Running testMemoizesNullUnsupported");
        
        // Note: Current implementation doesn't support null values
        // (null is used as the "not yet computed" sentinel)
        // This documents the current behavior
        
        AtomicInteger callCount = new AtomicInteger(0);
        
        LazyConstant<String> lazy = LazyConstant.of(() -> {
            callCount.incrementAndGet();
            return null; // Returns null
        });
        
        // Each call will recompute because null can't be cached
        lazy.get();
        lazy.get();
        
        // This shows the limitation - null values cause recomputation
        // In practice, JSON parsing doesn't return null from suppliers
        assertThat(callCount.get()).isGreaterThanOrEqualTo(2);
    }
}
