package pl.kaitou_dev.jaroscope.logging;

import java.io.StringWriter;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/** Formats log records as compact, human-readable lines. */
public final class LoggingFormatter extends Formatter {
  /** Timestamp format used in local stderr output. */
  private static final DateTimeFormatter TIMESTAMP_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

  /** ANSI sequence that restores terminal styling. */
  private static final String ANSI_RESET = "\u001B[0m";

  /** ANSI sequence for severe messages. */
  private static final String ANSI_RED = "\u001B[31m";

  /** ANSI sequence for warning messages. */
  private static final String ANSI_YELLOW = "\u001B[33m";

  /** ANSI sequence for informational messages. */
  private static final String ANSI_BLUE = "\u001B[34m";

  /** Platform line separator. */
  private static final String LINE_SEPARATOR = System.lineSeparator();

  /** Minimum width reserved for short level names. */
  private static final int LEVEL_WIDTH = 5;

  private final boolean colorsEnabled;

  /** Creates a formatter with the requested color behavior. */
  public LoggingFormatter(boolean colorsEnabled) {
    this.colorsEnabled = colorsEnabled;
  }

  /**
   * Formats one record with timestamp, level, thread, logger, message, and optional stack trace.
   *
   * @param record the record to format
   * @return one complete formatted log line
   */
  @Override
  public String format(LogRecord record) {
    String level = String.format("%-" + LEVEL_WIDTH + "s", record.getLevel().getName());
    String coloredLevel = colorize(level, record.getLevel());
    String logger = record.getLoggerName() == null ? "" : " " + record.getLoggerName();
    String message = formatMessage(record);
    String result =
        TIMESTAMP_FORMAT.format(record.getInstant())
            + " "
            + coloredLevel
            + " ["
            + Thread.currentThread().getName()
            + "]"
            + logger
            + " - "
            + message
            + LINE_SEPARATOR;
    if (record.getThrown() != null) {
      result += formatException(record);
    }
    return result;
  }

  /** Applies optional color to a padded level name. */
  private String colorize(String value, Level level) {
    if (!colorsEnabled) {
      return value;
    }
    String color =
        level.intValue() >= Level.SEVERE.intValue()
            ? ANSI_RED
            : level.intValue() >= Level.WARNING.intValue() ? ANSI_YELLOW : ANSI_BLUE;
    return color + value + ANSI_RESET;
  }

  /** Renders an attached exception using the standard Java stack-trace format. */
  private String formatException(LogRecord record) {
    StringWriter output = new StringWriter();
    record.getThrown().printStackTrace(new java.io.PrintWriter(output));
    return output.toString();
  }
}
