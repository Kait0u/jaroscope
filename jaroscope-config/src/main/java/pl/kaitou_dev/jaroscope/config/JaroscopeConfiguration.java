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
    List<Path> bannedRoots,
    long maxSourceResponseBytes,
    boolean backgroundDecompilationEnabled,
    int maxConcurrentBackgroundJars,
    int maxBackgroundClasses) {
  /** Creates a validated immutable configuration with defensive collection copying. */
  public JaroscopeConfiguration {
    Objects.requireNonNull(cacheDirectory, "cacheDirectory");
    Objects.requireNonNull(cacheMaxAge, "cacheMaxAge");
    Objects.requireNonNull(bannedRoots, "bannedRoots");
    if (targetRelease < 1) {
      throw new InvalidConfigurationException("targetRelease must be positive");
    }
    if (cacheMaxAge.isNegative() || cacheMaxAge.isZero()) {
      throw new InvalidConfigurationException("cacheMaxAge must be positive");
    }
    if (cacheMaxSizeBytes < 1) {
      throw new InvalidConfigurationException("cacheMaxSizeBytes must be positive");
    }
    if (maxSourceResponseBytes < 1) {
      throw new InvalidConfigurationException("maxSourceResponseBytes must be positive");
    }
    if (maxConcurrentBackgroundJars < 1) {
      throw new InvalidConfigurationException("maxConcurrentBackgroundJars must be positive");
    }
    if (maxBackgroundClasses < 1) {
      throw new InvalidConfigurationException("maxBackgroundClasses must be positive");
    }
    bannedRoots = List.copyOf(bannedRoots);
  }

  /** Creates configuration with the default source-response size limit. */
  public JaroscopeConfiguration(
      int targetRelease,
      Path cacheDirectory,
      Duration cacheMaxAge,
      long cacheMaxSizeBytes,
      List<Path> bannedRoots) {
    this(
        targetRelease,
        cacheDirectory,
        cacheMaxAge,
        cacheMaxSizeBytes,
        bannedRoots,
        ConfigurationConstants.DEFAULT_MAX_SOURCE_RESPONSE_BYTES,
        true,
        ConfigurationConstants.DEFAULT_MAX_BACKGROUND_JARS,
        ConfigurationConstants.DEFAULT_MAX_BACKGROUND_CLASSES);
  }

  /** Creates configuration with default response and background-decompilation settings. */
  public JaroscopeConfiguration(
      int targetRelease,
      Path cacheDirectory,
      Duration cacheMaxAge,
      long cacheMaxSizeBytes,
      List<Path> bannedRoots,
      long maxSourceResponseBytes) {
    this(
        targetRelease,
        cacheDirectory,
        cacheMaxAge,
        cacheMaxSizeBytes,
        bannedRoots,
        maxSourceResponseBytes,
        true,
        ConfigurationConstants.DEFAULT_MAX_BACKGROUND_JARS,
        ConfigurationConstants.DEFAULT_MAX_BACKGROUND_CLASSES);
  }
}
