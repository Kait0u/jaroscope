package pl.kaitou_dev.jaroscope.mcp;

/** Indicates that a get-class-source request is invalid or exceeds its response limit. */
public final class GetClassSourceException extends RuntimeException {
  /** Creates an exception with a diagnostic message. */
  public GetClassSourceException(String message) {
    super(message);
  }
}
