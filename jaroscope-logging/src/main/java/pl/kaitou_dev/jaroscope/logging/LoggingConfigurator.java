package pl.kaitou_dev.jaroscope.logging;

import java.util.Objects;

/** Configures Logback properties before the first SLF4J logger is created. */
public final class LoggingConfigurator {
  /** System property consumed by the Logback level converter. */
  public static final String COLOR_PROPERTY = "jaroscope.log.color";

  /** System property consumed by the Logback root logger configuration. */
  public static final String LEVEL_PROPERTY = "jaroscope.log.level";

  private LoggingConfigurator() {
    throw new AssertionError("No instances");
  }

  /**
   * Sets Logback properties for compact stderr logging.
   *
   * <p>This method must run before the first SLF4J logger is initialized.
   *
   * @param configuration the desired logging level and color behavior
   */
  public static void configure(LoggingConfiguration configuration) {
    Objects.requireNonNull(configuration, "configuration");
    System.setProperty(LEVEL_PROPERTY, configuration.minimumLevel().toString());
    System.setProperty(COLOR_PROPERTY, Boolean.toString(colorsEnabled(configuration)));
  }

  /** Resolves whether automatic color mode should emit ANSI sequences. */
  private static boolean colorsEnabled(LoggingConfiguration configuration) {
    return switch (configuration.colorMode()) {
      case ALWAYS -> true;
      case NEVER -> false;
      case AUTO -> System.console() != null;
    };
  }
}
