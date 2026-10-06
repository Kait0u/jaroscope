package pl.kaitou_dev.jaroscope.mcp;

/** Indicates that a list-classes request is invalid. */
public final class ListClassesException extends RuntimeException {
  /** Creates an exception with a diagnostic message. */
  public ListClassesException(String message) {
    super(message);
  }
}
