package pl.kaitou_dev.jaroscope.logging;

import java.io.PrintStream;
import java.util.Objects;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

/** Installs JARoscope logging on stderr without writing diagnostic output to stdout. */
public final class LoggingConfigurator {
  /** Name used by the JDK for the root logger. */
  private static final String ROOT_LOGGER_NAME = "";

  private LoggingConfigurator() {
    throw new AssertionError("No instances");
  }

  /**
   * Replaces root handlers with one formatted handler writing to the supplied stderr stream.
   *
   * @param configuration logging level and color configuration
   * @param stderr the stream reserved for diagnostics
   */
  public static void configure(LoggingConfiguration configuration, PrintStream stderr) {
    Objects.requireNonNull(configuration, "configuration");
    Objects.requireNonNull(stderr, "stderr");
    Logger root = Logger.getLogger(ROOT_LOGGER_NAME);
    for (Handler handler : root.getHandlers()) {
      root.removeHandler(handler);
      handler.close();
    }
    root.setLevel(configuration.minimumLevel());
    StderrHandler handler =
        new StderrHandler(stderr, new LoggingFormatter(colorsEnabled(configuration, stderr)));
    handler.setLevel(Level.ALL);
    root.addHandler(handler);
  }

  /** Resolves whether automatic color mode should emit ANSI sequences. */
  private static boolean colorsEnabled(LoggingConfiguration configuration, PrintStream stderr) {
    return switch (configuration.colorMode()) {
      case ALWAYS -> true;
      case NEVER -> false;
      case AUTO -> stderr == System.err && System.console() != null;
    };
  }

  /** Flushes each record immediately to preserve diagnostics around protocol requests. */
  private static final class StderrHandler extends StreamHandler {
    private final PrintStream stderr;

    /** Creates a handler bound to the configured stderr stream and formatter. */
    private StderrHandler(PrintStream stderr, LoggingFormatter formatter) {
      this.stderr = stderr;
      setOutputStream(stderr);
      setFormatter(formatter);
    }

    @Override
    public synchronized void publish(java.util.logging.LogRecord record) {
      super.publish(record);
      flush();
    }

    @Override
    public synchronized void close() {
      flush();
      stderr.flush();
    }
  }
}
