package pl.kaitou_dev.jaroscope.logging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Verifies compact level rendering without testing Logback internals. */
class LevelColorConverterTest {
  /** Clears the process property changed by each converter test. */
  @AfterEach
  void clearColorProperty() {
    System.clearProperty(LoggingConfigurator.COLOR_PROPERTY);
  }

  /** Confirms that disabled color mode emits plain aligned text. */
  @Test
  void formatsPlainLevel() {
    System.setProperty(LoggingConfigurator.COLOR_PROPERTY, Boolean.FALSE.toString());

    LoggerContext context = new LoggerContext();
    Logger logger = context.getLogger("test");
    String output =
        new LevelColorConverter()
            .convert(new LoggingEvent("test", logger, Level.INFO, "", null, null));

    assertTrue(output.startsWith("INFO"));
    assertFalse(output.contains("\u001B"));
  }

  /** Confirms that enabled color mode wraps warning levels in ANSI sequences. */
  @Test
  void formatsColoredWarningLevel() {
    System.setProperty(LoggingConfigurator.COLOR_PROPERTY, Boolean.TRUE.toString());

    LoggerContext context = new LoggerContext();
    Logger logger = context.getLogger("test");
    String output =
        new LevelColorConverter()
            .convert(new LoggingEvent("test", logger, Level.WARN, "", null, null));

    assertTrue(output.contains("\u001B[33m"));
    assertTrue(output.contains("WARN"));
  }
}
