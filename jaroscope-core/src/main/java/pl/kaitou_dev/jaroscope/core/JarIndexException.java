package pl.kaitou_dev.jaroscope.core;

/** Indicates that a JAR indexing request violates the indexing contract. */
public final class JarIndexException extends RuntimeException {
  /** Creates an exception with a diagnostic message. */
  public JarIndexException(String message) {
    super(message);
  }
}
