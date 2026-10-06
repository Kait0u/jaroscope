package pl.kaitou_dev.jaroscope.config;

/** Indicates that configuration values do not satisfy JARoscope's rules. */
public final class InvalidConfigurationException extends ConfigurationException {
  /** Creates an exception with a diagnostic message. */
  public InvalidConfigurationException(String message) {
    super(message);
  }

  /** Creates an exception with a diagnostic message and underlying cause. */
  public InvalidConfigurationException(String message, Throwable cause) {
    super(message, cause);
  }
}
