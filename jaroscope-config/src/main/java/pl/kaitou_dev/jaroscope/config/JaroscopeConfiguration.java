package pl.kaitou_dev.jaroscope.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import pl.kaitou_dev.jaroscope.core.ArchiveLimits;

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
    int maxBackgroundClasses,
    long maxArchiveBytes,
    int maxArchiveEntries,
    long maxClassFileBytes,
    long maxExpandedClassBytes,
    int maxConcurrentVineflowerRuns,
    int vineflowerThreadsPerRun,
    long maxJarResourceBytes) {
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
    if (maxJarResourceBytes < 1) {
      throw new InvalidConfigurationException("maxJarResourceBytes must be positive");
    }
    if (maxConcurrentBackgroundJars < 1) {
      throw new InvalidConfigurationException("maxConcurrentBackgroundJars must be positive");
    }
    if (maxBackgroundClasses < 1) {
      throw new InvalidConfigurationException("maxBackgroundClasses must be positive");
    }
    if (maxArchiveBytes < 1L || maxArchiveEntries < 1 || maxClassFileBytes < 1L) {
      throw new InvalidConfigurationException("Archive resource limits must be positive");
    }
    if (maxClassFileBytes >= Integer.MAX_VALUE || maxExpandedClassBytes < maxClassFileBytes) {
      throw new InvalidConfigurationException("Class resource limits are inconsistent");
    }
    if (maxConcurrentVineflowerRuns < 1 || vineflowerThreadsPerRun < 1) {
      throw new InvalidConfigurationException("Vineflower concurrency limits must be positive");
    }
    if (backgroundDecompilationEnabled && maxConcurrentVineflowerRuns < 2) {
      throw new InvalidConfigurationException(
          "At least two Vineflower runs are required when background decompilation is enabled");
    }
    bannedRoots = List.copyOf(bannedRoots);
  }

  /** Creates configuration with the default JAR resource response limit. */
  public JaroscopeConfiguration(
      int targetRelease,
      Path cacheDirectory,
      Duration cacheMaxAge,
      long cacheMaxSizeBytes,
      List<Path> bannedRoots,
      long maxSourceResponseBytes,
      boolean backgroundDecompilationEnabled,
      int maxConcurrentBackgroundJars,
      int maxBackgroundClasses,
      long maxArchiveBytes,
      int maxArchiveEntries,
      long maxClassFileBytes,
      long maxExpandedClassBytes,
      int maxConcurrentVineflowerRuns,
      int vineflowerThreadsPerRun) {
    this(
        targetRelease,
        cacheDirectory,
        cacheMaxAge,
        cacheMaxSizeBytes,
        bannedRoots,
        maxSourceResponseBytes,
        backgroundDecompilationEnabled,
        maxConcurrentBackgroundJars,
        maxBackgroundClasses,
        maxArchiveBytes,
        maxArchiveEntries,
        maxClassFileBytes,
        maxExpandedClassBytes,
        maxConcurrentVineflowerRuns,
        vineflowerThreadsPerRun,
        ConfigurationConstants.DEFAULT_MAX_JAR_RESOURCE_BYTES);
  }

  /** Returns the archive limits consumed by indexing and class extraction. */
  public ArchiveLimits archiveLimits() {
    return new ArchiveLimits(
        maxArchiveBytes, maxArchiveEntries, maxClassFileBytes, maxExpandedClassBytes);
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

  /** Creates configuration with the default archive and decompiler resource limits. */
  public JaroscopeConfiguration(
      int targetRelease,
      Path cacheDirectory,
      Duration cacheMaxAge,
      long cacheMaxSizeBytes,
      List<Path> bannedRoots,
      long maxSourceResponseBytes,
      boolean backgroundDecompilationEnabled,
      int maxConcurrentBackgroundJars,
      int maxBackgroundClasses) {
    this(
        targetRelease,
        cacheDirectory,
        cacheMaxAge,
        cacheMaxSizeBytes,
        bannedRoots,
        maxSourceResponseBytes,
        backgroundDecompilationEnabled,
        maxConcurrentBackgroundJars,
        maxBackgroundClasses,
        ConfigurationConstants.DEFAULT_MAX_ARCHIVE_BYTES,
        ConfigurationConstants.DEFAULT_MAX_ARCHIVE_ENTRIES,
        ConfigurationConstants.DEFAULT_MAX_CLASS_FILE_BYTES,
        ConfigurationConstants.DEFAULT_MAX_EXPANDED_CLASS_BYTES,
        ConfigurationConstants.DEFAULT_MAX_CONCURRENT_VINEFLOWER_RUNS,
        ConfigurationConstants.DEFAULT_VINEFLOWER_THREADS_PER_RUN);
  }
}
