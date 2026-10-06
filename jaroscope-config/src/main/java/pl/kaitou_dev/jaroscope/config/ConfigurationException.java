package pl.kaitou_dev.jaroscope.config;

/** Base exception for invalid or unusable JARoscope configuration. */
public class ConfigurationException extends RuntimeException {
  /** Creates an exception with a diagnostic message. */
  public ConfigurationException(String message) {
    super(message);
  }

  /** Creates an exception with a diagnostic message and underlying cause. */
  public ConfigurationException(String message, Throwable cause) {
    super(message, cause);
  }
}
