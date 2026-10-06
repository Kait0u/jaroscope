package pl.kaitou_dev.jaroscope.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Validated runtime configuration for JARoscope. */
public record JaroscopeConfiguration(
    int targetRelease,
    Path cacheDirectory,
    Duration cacheMaxAge,
    long cacheMaxSizeBytes,
    List<Path> bannedRoots) {
  /** Creates a validated immutable configuration with defensive collection copying. */
  public JaroscopeConfiguration {
    Objects.requireNonNull(cacheDirectory, "cacheDirectory");
    Objects.requireNonNull(cacheMaxAge, "cacheMaxAge");
    Objects.requireNonNull(bannedRoots, "bannedRoots");
    if (targetRelease < 1) {
      throw new IllegalArgumentException("targetRelease must be positive");
    }
    if (cacheMaxAge.isNegative() || cacheMaxAge.isZero()) {
      throw new IllegalArgumentException("cacheMaxAge must be positive");
    }
    if (cacheMaxSizeBytes < 1) {
      throw new IllegalArgumentException("cacheMaxSizeBytes must be positive");
    }
    bannedRoots = List.copyOf(bannedRoots);
  }
}
