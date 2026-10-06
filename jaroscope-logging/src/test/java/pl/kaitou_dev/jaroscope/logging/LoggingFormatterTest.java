package pl.kaitou_dev.jaroscope.logging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;

/** Verifies the compact stderr format and color behavior. */
class LoggingFormatterTest {
  /** Confirms that plain output contains the diagnostic context without ANSI escapes. */
  @Test
  void formatsPlainRecord() {
    LogRecord record = new LogRecord(Level.INFO, "Configuration loaded targetRelease=21");
    record.setLoggerName("jaroscope.application");
    record.setInstant(Instant.parse("2026-10-06T18:42:11.284Z"));

    String output = new LoggingFormatter(false).format(record);

    assertTrue(output.contains("INFO  "));
    assertTrue(output.contains("jaroscope.application"));
    assertTrue(output.contains("Configuration loaded targetRelease=21"));
    assertFalse(output.contains("\u001B"));
  }

  /** Confirms that enabled colors affect only the formatted level field. */
  @Test
  void formatsColoredRecord() {
    LogRecord record = new LogRecord(Level.WARNING, "Cache miss");

    String output = new LoggingFormatter(true).format(record);

    assertTrue(output.contains("\u001B[33m"));
    assertTrue(output.contains("Cache miss"));
  }
}
