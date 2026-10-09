package pl.kaitou_dev.jaroscope.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies layered YAML configuration and validation. */
class ConfigurationLoaderTest {
  private static final String USER_CONFIGURATION = "jaroscope:\n  target-release: 17\n";
  private static final String EXPLICIT_CONFIGURATION =
      """
      jaroscope:
        cache:
          max-size: 1MiB
        response:
          max-jar-resource-size: 3KiB
        archive:
          max-size: 512MiB
          max-entries: 50000
          max-class-size: 32MiB
          max-expanded-class-size: 1GiB
        decompiler:
          max-concurrent-runs: 3
          threads-per-run: 3
      """;

  @TempDir Path temporaryDirectory;

  /** Confirms that home settings are loaded after bundled defaults. */
  @Test
  void loadsHomeConfigurationOverDefaults() throws Exception {
    Path homeConfiguration =
        temporaryDirectory
            .resolve(".jaroscope")
            .resolve(ConfigurationConstants.CONFIGURATION_FILE_NAME);
    Files.createDirectories(homeConfiguration.getParent());
    Files.writeString(homeConfiguration, USER_CONFIGURATION);

    JaroscopeConfiguration configuration =
        new ConfigurationLoader().load(temporaryDirectory, Optional.empty());

    assertEquals(17, configuration.targetRelease());
    assertEquals(
        ConfigurationConstants.DEFAULT_CACHE_MAX_SIZE_BYTES, configuration.cacheMaxSizeBytes());
    assertEquals(
        ConfigurationConstants.DEFAULT_MAX_SOURCE_RESPONSE_BYTES,
        configuration.maxSourceResponseBytes());
    assertTrue(configuration.backgroundDecompilationEnabled());
    assertEquals(
        ConfigurationConstants.DEFAULT_MAX_BACKGROUND_JARS,
        configuration.maxConcurrentBackgroundJars());
    assertEquals(
        ConfigurationConstants.DEFAULT_MAX_BACKGROUND_CLASSES,
        configuration.maxBackgroundClasses());
    assertEquals(ConfigurationConstants.DEFAULT_MAX_ARCHIVE_BYTES, configuration.maxArchiveBytes());
    assertEquals(
        ConfigurationConstants.DEFAULT_MAX_ARCHIVE_ENTRIES, configuration.maxArchiveEntries());
    assertEquals(
        ConfigurationConstants.DEFAULT_MAX_CLASS_FILE_BYTES, configuration.maxClassFileBytes());
    assertEquals(
        ConfigurationConstants.DEFAULT_MAX_EXPANDED_CLASS_BYTES,
        configuration.maxExpandedClassBytes());
    assertEquals(
        ConfigurationConstants.DEFAULT_MAX_CONCURRENT_VINEFLOWER_RUNS,
        configuration.maxConcurrentVineflowerRuns());
    assertEquals(
        ConfigurationConstants.DEFAULT_VINEFLOWER_THREADS_PER_RUN,
        configuration.vineflowerThreadsPerRun());
    assertEquals(ConfigurationConstants.DEFAULT_MAX_ARCHIVE_BYTES, configuration.maxArchiveBytes());
    assertEquals(
        ConfigurationConstants.DEFAULT_MAX_ARCHIVE_ENTRIES, configuration.maxArchiveEntries());
    assertEquals(
        ConfigurationConstants.DEFAULT_MAX_CLASS_FILE_BYTES, configuration.maxClassFileBytes());
    assertEquals(
        ConfigurationConstants.DEFAULT_MAX_EXPANDED_CLASS_BYTES,
        configuration.maxExpandedClassBytes());
    assertEquals(
        ConfigurationConstants.DEFAULT_MAX_CONCURRENT_VINEFLOWER_RUNS,
        configuration.maxConcurrentVineflowerRuns());
    assertEquals(
        ConfigurationConstants.DEFAULT_VINEFLOWER_THREADS_PER_RUN,
        configuration.vineflowerThreadsPerRun());
    assertEquals(
        ConfigurationConstants.DEFAULT_MAX_JAR_RESOURCE_BYTES, configuration.maxJarResourceBytes());
  }

  /** Confirms that an explicit file overrides the home file while preserving other settings. */
  @Test
  void loadsExplicitConfigurationOverHomeConfiguration() throws Exception {
    Path homeConfiguration =
        temporaryDirectory
            .resolve(".jaroscope")
            .resolve(ConfigurationConstants.CONFIGURATION_FILE_NAME);
    Files.createDirectories(homeConfiguration.getParent());
    Files.writeString(homeConfiguration, USER_CONFIGURATION);
    Path explicitConfiguration = temporaryDirectory.resolve("override.yml");
    Files.writeString(explicitConfiguration, EXPLICIT_CONFIGURATION);

    JaroscopeConfiguration configuration =
        new ConfigurationLoader().load(temporaryDirectory, Optional.of(explicitConfiguration));

    assertEquals(17, configuration.targetRelease());
    assertEquals(1024L * 1024L, configuration.cacheMaxSizeBytes());
    assertEquals(512L * 1024L * 1024L, configuration.maxArchiveBytes());
    assertEquals(50_000, configuration.maxArchiveEntries());
    assertEquals(32L * 1024L * 1024L, configuration.maxClassFileBytes());
    assertEquals(1024L * 1024L * 1024L, configuration.maxExpandedClassBytes());
    assertEquals(3, configuration.maxConcurrentVineflowerRuns());
    assertEquals(3, configuration.vineflowerThreadsPerRun());
    assertEquals(3L * 1024L, configuration.maxJarResourceBytes());
  }

  /** Confirms that invalid duration units are rejected during configuration loading. */
  @Test
  void rejectsInvalidCacheAgeUnit() throws Exception {
    Path explicitConfiguration = temporaryDirectory.resolve("invalid.yml");
    Files.writeString(explicitConfiguration, "jaroscope:\n  cache:\n    max-age: 30h\n");

    assertThrows(
        InvalidConfigurationException.class,
        () ->
            new ConfigurationLoader().load(temporaryDirectory, Optional.of(explicitConfiguration)));
  }
}
