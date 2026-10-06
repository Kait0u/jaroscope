package pl.kaitou_dev.jaroscope.logging;

import java.util.Objects;
import java.util.logging.Level;

/** Immutable configuration for JARoscope's stderr logging. */
public record LoggingConfiguration(ColorMode colorMode, Level minimumLevel) {
  /** Creates a validated logging configuration. */
  public LoggingConfiguration {
    Objects.requireNonNull(colorMode, "colorMode");
    Objects.requireNonNull(minimumLevel, "minimumLevel");
  }

  /** Returns the default compact logging configuration. */
  public static LoggingConfiguration defaults() {
    return new LoggingConfiguration(ColorMode.AUTO, Level.INFO);
  }
}
