package pl.kaitou_dev.jaroscope.mcp;

/** Indicates that a get-class-interface request is invalid. */
public final class GetClassInterfaceException extends RuntimeException {
  /** Creates an exception with a diagnostic message. */
  public GetClassInterfaceException(String message) {
    super(message);
  }
}
