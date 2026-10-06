package pl.kaitou_dev.jaroscope.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/** Renders Logback levels with optional ANSI colors while preserving plain output when disabled. */
public final class LevelColorConverter extends ClassicConverter {
  /** ANSI sequence that restores terminal styling. */
  private static final String ANSI_RESET = "\u001B[0m";

  /** ANSI sequence for severe messages. */
  private static final String ANSI_RED = "\u001B[31m";

  /** ANSI sequence for warning messages. */
  private static final String ANSI_YELLOW = "\u001B[33m";

  /** ANSI sequence for informational messages. */
  private static final String ANSI_BLUE = "\u001B[34m";

  /** Width used to align short level names. */
  private static final int LEVEL_WIDTH = 5;

  /**
   * Converts one Logback event's level to the configured compact representation.
   *
   * @param event the event being formatted
   * @return the padded level, optionally wrapped in ANSI color sequences
   */
  @Override
  public String convert(ILoggingEvent event) {
    String level = String.format("%-" + LEVEL_WIDTH + "s", event.getLevel().toString());
    if (!Boolean.getBoolean(LoggingConfigurator.COLOR_PROPERTY)) {
      return level;
    }
    String color =
        event.getLevel().toInt() >= Level.ERROR_INT
            ? ANSI_RED
            : event.getLevel().toInt() >= Level.WARN_INT ? ANSI_YELLOW : ANSI_BLUE;
    return color + level + ANSI_RESET;
  }
}
