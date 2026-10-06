package pl.kaitou_dev.jaroscope.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies layered YAML configuration and validation. */
class ConfigurationLoaderTest {
  private static final String USER_CONFIGURATION = "jaroscope:\n  target-release: 17\n";
  private static final String EXPLICIT_CONFIGURATION = "jaroscope:\n  cache:\n    max-size: 1MiB\n";

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
