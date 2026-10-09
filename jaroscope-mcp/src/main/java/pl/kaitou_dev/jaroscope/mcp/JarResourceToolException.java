package pl.kaitou_dev.jaroscope.mcp;

/** Indicates that a JAR resource tool request is invalid. */
public final class JarResourceToolException extends RuntimeException {
  /** Creates an exception with a diagnostic message. */
  public JarResourceToolException(String message) {
    super(message);
  }
}
