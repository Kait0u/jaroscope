package pl.kaitou_dev.jaroscope.decompiler;

/** Indicates that a class could not be decompiled into source. */
public final class DecompilerException extends RuntimeException {
  /** Creates an exception with a diagnostic message. */
  public DecompilerException(String message) {
    super(message);
  }

  /** Creates an exception with a diagnostic message and underlying cause. */
  public DecompilerException(String message, Throwable cause) {
    super(message, cause);
  }
}
