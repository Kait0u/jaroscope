package pl.kaitou_dev.jaroscope.cache;

/** Indicates an invalid or unusable JARoscope cache operation. */
public final class CacheException extends RuntimeException {
  /** Creates an exception with a diagnostic message and underlying cause. */
  public CacheException(String message, Throwable cause) {
    super(message, cause);
  }
}
