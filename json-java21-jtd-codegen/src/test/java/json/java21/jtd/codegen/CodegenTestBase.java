package json.java21.jtd.codegen;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import java.util.Locale;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/// Base class for all codegen tests.
///
/// Mirrors the JTD module's JUL setup so tests behave similarly when run
/// standalone from this module.
public class CodegenTestBase {

  static final Logger LOG = Logger.getLogger("json.java21.jtd.codegen");

  @BeforeAll
  static void configureJul() {
    Logger root = Logger.getLogger("");
    String levelProp = System.getProperty("java.util.logging.ConsoleHandler.level");
    Level targetLevel = Level.INFO;
    if (levelProp != null) {
      try {
        targetLevel = Level.parse(levelProp.trim());
      } catch (IllegalArgumentException ex) {
        try {
          targetLevel = Level.parse(levelProp.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
          System.err.println("Unrecognized logging level from 'java.util.logging.ConsoleHandler.level': " + levelProp);
        }
      }
    }
    if (root.getLevel() == null || root.getLevel().intValue() > targetLevel.intValue()) {
      root.setLevel(targetLevel);
    }
    for (Handler handler : root.getHandlers()) {
      Level handlerLevel = handler.getLevel();
      if (handlerLevel == null || handlerLevel.intValue() > targetLevel.intValue()) {
        handler.setLevel(targetLevel);
      }
    }
  }

  @BeforeEach
  void announce(TestInfo testInfo) {
    final String cls = testInfo.getTestClass().map(Class::getSimpleName).orElse("UnknownTest");
    final String name = testInfo.getTestMethod().map(java.lang.reflect.Method::getName)
        .orElseGet(testInfo::getDisplayName);
    LOG.info(() -> "TEST: " + cls + "#" + name);
  }
}
