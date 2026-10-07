package pl.kaitou_dev.jaroscope.mcp;

/** Indicates that a bulk decompilation request is invalid. */
public final class DecompileJarException extends RuntimeException {
  /** Creates an exception with a diagnostic message. */
  public DecompileJarException(String message) {
    super(message);
  }
}
