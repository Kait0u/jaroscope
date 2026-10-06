package pl.kaitou_dev.jaroscope.core;

/** Indicates that a class-interface request cannot be fulfilled. */
public final class ClassInterfaceException extends RuntimeException {
  /** Creates an exception with a diagnostic message. */
  public ClassInterfaceException(String message) {
    super(message);
  }

  /** Creates an exception with a diagnostic message and underlying cause. */
  public ClassInterfaceException(String message, Throwable cause) {
    super(message, cause);
  }
}
