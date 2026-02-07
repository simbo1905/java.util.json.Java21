/// Vanilla JS test runner for bun - zero dependencies, ESM2020
/// Usage: bun run test-runner.js <test-module.js>
/// Or programmatically from Java tests

const FAIL = '\x1b[31m✗\x1b[0m';
const PASS = '\x1b[32m✓\x1b[0m';

let totalTests = 0;
let passedTests = 0;
let failedTests = 0;
const failures = [];

/// Assert that two values are strictly equal
function assertEq(actual, expected, message) {
    if (actual !== expected) {
        throw new Error(`${message || 'Assertion failed'}: expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);
    }
}

/// Assert that value is truthy
function assertTrue(value, message) {
    if (!value) {
        throw new Error(message || 'Expected truthy value');
    }
}

/// Assert that value is falsy
function assertFalse(value, message) {
    if (value) {
        throw new Error(message || 'Expected falsy value');
    }
}

/// Assert that two arrays are deeply equal
function assertArrayEq(actual, expected, message) {
    if (!Array.isArray(actual) || !Array.isArray(expected)) {
        throw new Error(`${message || 'Not arrays'}: expected array, got ${typeof actual}`);
    }
    if (actual.length !== expected.length) {
        throw new Error(`${message || 'Array length mismatch'}: expected ${expected.length}, got ${actual.length}`);
    }
    for (let i = 0; i < actual.length; i++) {
        if (JSON.stringify(actual[i]) !== JSON.stringify(expected[i])) {
            throw new Error(`${message || 'Array element mismatch'} at index ${i}: expected ${JSON.stringify(expected[i])}, got ${JSON.stringify(actual[i])}`);
        }
    }
}

/// Assert that two objects are deeply equal (shallow comparison)
function assertObjEq(actual, expected, message) {
    const actualKeys = Object.keys(actual).sort();
    const expectedKeys = Object.keys(expected).sort();
    if (actualKeys.length !== expectedKeys.length) {
        throw new Error(`${message || 'Object key count mismatch'}: expected ${expectedKeys.length} keys, got ${actualKeys.length}`);
    }
    for (const key of actualKeys) {
        if (JSON.stringify(actual[key]) !== JSON.stringify(expected[key])) {
            throw new Error(`${message || 'Object value mismatch'} at key "${key}": expected ${JSON.stringify(expected[key])}, got ${JSON.stringify(actual[key])}`);
        }
    }
}

/// Run a test function
function test(name, fn) {
    totalTests++;
    try {
        fn();
        passedTests++;
        console.log(`${PASS} ${name}`);
        return true;
    } catch (e) {
        failedTests++;
        failures.push({ name, error: e.message });
        console.log(`${FAIL} ${name}`);
        console.log(`  ${e.message}`);
        return false;
    }
}

/// Run all tests and print summary
function runTests() {
    console.log(`\n${'='.repeat(50)}`);
    console.log(`Total: ${totalTests}, Passed: ${passedTests}, Failed: ${failedTests}`);
    
    if (failedTests > 0) {
        console.log(`\nFailures:`);
        for (const f of failures) {
            console.log(`  - ${f.name}: ${f.error}`);
        }
        process.exit(1);
    } else {
        console.log('\nAll tests passed!');
        process.exit(0);
    }
}

/// Load and run a test module
async function runTestModule(modulePath) {
    try {
        const module = await import(modulePath);
        
        // If module exports a run() function, call it
        if (typeof module.run === 'function') {
            await module.run();
        }
        
        // Run any tests that were defined
        runTests();
    } catch (e) {
        console.error(`Failed to load test module ${modulePath}: ${e.message}`);
        process.exit(1);
    }
}

/// Main entry point when run directly
if (import.meta.main) {
    const args = process.argv.slice(2);
    if (args.length < 1) {
        console.error('Usage: bun run test-runner.js <test-module.js>');
        process.exit(1);
    }
    
    // Resolve path relative to current working directory
    const path = await import('path');
    const modulePath = path.resolve(args[0]);
    await runTestModule('file://' + modulePath);
}

export { test, assertEq, assertTrue, assertFalse, assertArrayEq, assertObjEq, runTests, runTestModule };
