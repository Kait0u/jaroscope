package pl.kaitou_dev.jaroscope.logging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/** Verifies that configured diagnostics are emitted to the supplied stderr stream. */
class LoggingConfiguratorTest {
  /** Confirms that configuration writes logs to stderr and not stdout. */
  @Test
  void writesToConfiguredStderr() {
    ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
    PrintStream stderr = new PrintStream(stderrBytes, true, StandardCharsets.UTF_8);

    LoggingConfigurator.configure(
        new LoggingConfiguration(ColorMode.NEVER, java.util.logging.Level.INFO), stderr);
    Logger.getLogger("jaroscope.test").info("stderr-only event");

    String output = stderrBytes.toString(StandardCharsets.UTF_8);
    assertTrue(output.contains("stderr-only event"));
    assertFalse(output.contains("stdout-only event"));
  }
}
