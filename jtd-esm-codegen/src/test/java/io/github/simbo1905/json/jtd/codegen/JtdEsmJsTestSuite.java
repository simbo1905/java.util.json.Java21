package io.github.simbo1905.json.jtd.codegen;

import org.bitbucket.thinbus.junitjs.JSRunner;
import org.bitbucket.thinbus.junitjs.Tests;
import org.junit.runner.RunWith;

/// JUnit test suite that runs JavaScript tests via GraalVM polyglot.
/// Uses junit-js JSRunner to execute .js test files from `src/test/resources/`.
/// Each JS file uses the `tests({...})` pattern from JUnitJSUtils.js.
///
/// This replaces the previous bun-based JS test execution that required
/// an external JavaScript runtime not available in the CI image.
///
/// Discovered by Surefire via the JUnit Vintage engine (JUnit 4 runner
/// under JUnit Platform). The class name ends in "Test" so that Surefire's
/// default includes pattern picks it up.
@Tests({
    "boolean-schema.test.js",
    "nested-elements-empty-focused.test.js"
})
@RunWith(JSRunner.class)
public class JtdEsmJsTestSuite {
}
