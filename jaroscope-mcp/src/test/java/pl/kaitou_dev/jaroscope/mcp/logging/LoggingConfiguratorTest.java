package pl.kaitou_dev.jaroscope.mcp.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

/** Verifies that startup configuration is passed to Logback through system properties. */
class LoggingConfiguratorTest {
  /** Clears configuration properties after each isolated test. */
  @AfterEach
  void clearProperties() {
    System.clearProperty(LoggingConfigurator.COLOR_PROPERTY);
    System.clearProperty(LoggingConfigurator.LEVEL_PROPERTY);
  }

  /** Confirms that explicit level and color settings are published before logger startup. */
  @Test
  void publishesLoggingProperties() {
    LoggingConfigurator.configure(new LoggingConfiguration(ColorMode.NEVER, Level.WARN));

    assertEquals("WARN", System.getProperty(LoggingConfigurator.LEVEL_PROPERTY));
    assertEquals("false", System.getProperty(LoggingConfigurator.COLOR_PROPERTY));
  }
}
