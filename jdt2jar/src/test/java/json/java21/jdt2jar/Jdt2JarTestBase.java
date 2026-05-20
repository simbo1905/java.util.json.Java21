package json.java21.jdt2jar;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import java.util.Locale;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/// Shared JUL bootstrap for jdt2jar tests.
public class Jdt2JarTestBase {

  static final Logger LOG = Logger.getLogger("json.java21.jdt2jar");

  @BeforeAll
  static void configureJul() {
    final var root = Logger.getLogger("");
    final var levelProp = System.getProperty("java.util.logging.ConsoleHandler.level");
    var targetLevel = Level.INFO;
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
    for (final var handler : root.getHandlers()) {
      final var handlerLevel = handler.getLevel();
      if (handlerLevel == null || handlerLevel.intValue() > targetLevel.intValue()) {
        handler.setLevel(targetLevel);
      }
    }
  }

  @BeforeEach
  void announce(TestInfo testInfo) {
    final var cls = testInfo.getTestClass().map(Class::getSimpleName).orElse("UnknownTest");
    final var name = testInfo.getTestMethod().map(java.lang.reflect.Method::getName)
        .orElseGet(testInfo::getDisplayName);
    LOG.info(() -> "TEST: " + cls + "#" + name);
  }
}
