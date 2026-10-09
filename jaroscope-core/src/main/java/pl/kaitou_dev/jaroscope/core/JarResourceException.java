package pl.kaitou_dev.jaroscope.core;

/** Indicates that a JAR resource is missing or exceeds the configured read limit. */
public final class JarResourceException extends RuntimeException {
  /** Creates an exception with a diagnostic message. */
  public JarResourceException(String message) {
    super(message);
  }
}
