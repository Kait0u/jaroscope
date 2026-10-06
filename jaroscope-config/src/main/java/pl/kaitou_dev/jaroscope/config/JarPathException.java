package pl.kaitou_dev.jaroscope.config;

import java.io.IOException;

/** Indicates that a requested JAR path violates JARoscope's path policy. */
public final class JarPathException extends IOException {
  /** Creates an exception with a diagnostic message. */
  public JarPathException(String message) {
    super(message);
  }
}
