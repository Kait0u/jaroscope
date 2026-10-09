package pl.kaitou_dev.jaroscope.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Loads and validates layered YAML configuration for JARoscope. */
public final class ConfigurationLoader {
  /** The resource path for bundled configuration defaults. */
  private static final String DEFAULTS_RESOURCE = "/application.yml";

  /** The YAML key for the target Java release. */
  private static final String TARGET_RELEASE_KEY = "target-release";

  /** The YAML key for cache settings. */
  private static final String CACHE_KEY = "cache";

  /** The YAML key for the cache directory. */
  private static final String CACHE_DIRECTORY_KEY = "directory";

  /** The YAML key for cache retention. */
  private static final String CACHE_MAX_AGE_KEY = "max-age";

  /** The YAML key for the cache size limit. */
  private static final String CACHE_MAX_SIZE_KEY = "max-size";

  /** The YAML key for security settings. */
  private static final String SECURITY_KEY = "security";

  /** The YAML key for banned filesystem roots. */
  private static final String BANNED_ROOTS_KEY = "banned-roots";

  /** The YAML key for response settings. */
  private static final String RESPONSE_KEY = "response";

  /** The YAML key for the maximum source response size. */
  private static final String MAX_SOURCE_SIZE_KEY = "max-source-size";

  /** The YAML key for the maximum raw JAR resource response size. */
  private static final String MAX_JAR_RESOURCE_SIZE_KEY = "max-jar-resource-size";

  /** The YAML key for background decompilation settings. */
  private static final String BACKGROUND_DECOMPILATION_KEY = "background-decompilation";

  /** The YAML key enabling background decompilation. */
  private static final String BACKGROUND_ENABLED_KEY = "enabled";

  /** The YAML key limiting concurrent background JAR jobs. */
  private static final String MAX_CONCURRENT_BACKGROUND_JARS_KEY = "max-concurrent-jars";

  /** The YAML key limiting classes warmed for one JAR. */
  private static final String MAX_BACKGROUND_CLASSES_KEY = "max-classes";

  /** The YAML key for archive resource limits. */
  private static final String ARCHIVE_KEY = "archive";

  /** The YAML key for maximum compressed archive size. */
  private static final String MAX_ARCHIVE_SIZE_KEY = "max-size";

  /** The YAML key for maximum central-directory entry count. */
  private static final String MAX_ARCHIVE_ENTRIES_KEY = "max-entries";

  /** The YAML key for maximum uncompressed class entry size. */
  private static final String MAX_CLASS_SIZE_KEY = "max-class-size";

  /** The YAML key for maximum total selected class bytes staged for the decompiler. */
  private static final String MAX_EXPANDED_CLASS_SIZE_KEY = "max-expanded-class-size";

  /** The YAML key for decompiler execution limits. */
  private static final String DECOMPILER_KEY = "decompiler";

  /** The YAML key for concurrent Vineflower runs. */
  private static final String MAX_CONCURRENT_VINEFLOWER_RUNS_KEY = "max-concurrent-runs";

  /** The YAML key for threads used by each Vineflower run. */
  private static final String VINEFLOWER_THREADS_PER_RUN_KEY = "threads-per-run";

  /** The supported suffix for day durations. */
  private static final String DAYS_SUFFIX = "d";

  /** The supported suffix for byte counts. */
  private static final String BYTES_SUFFIX = "B";

  /** The supported suffix for kibibyte counts. */
  private static final String KIBIBYTES_SUFFIX = "KiB";

  /** The supported suffix for mebibyte counts. */
  private static final String MEBIBYTES_SUFFIX = "MiB";

  /** The supported suffix for gibibyte counts. */
  private static final String GIBIBYTES_SUFFIX = "GiB";

  /** The number of bytes in one kibibyte. */
  private static final long BYTES_PER_KIBIBYTE = 1024L;

  /** The number of bytes in one mebibyte. */
  private static final long BYTES_PER_MEBIBYTE = BYTES_PER_KIBIBYTE * 1024L;

  /** The number of bytes in one gibibyte. */
  private static final long BYTES_PER_GIBIBYTE = BYTES_PER_MEBIBYTE * 1024L;

  private final ObjectMapper mapper;

  /** Creates a loader using Jackson's YAML mapper. */
  public ConfigurationLoader() {
    mapper = new ObjectMapper(new YAMLFactory());
  }

  /**
   * Loads defaults, the user's home configuration, and an optional explicit override in that order.
   *
   * @param userHome the user's home directory
   * @param explicitConfiguration an optional explicit configuration file
   * @return the validated merged configuration
   * @throws IOException if a configuration file cannot be read or parsed
   */
  public JaroscopeConfiguration load(Path userHome, Optional<Path> explicitConfiguration)
      throws IOException {
    Objects.requireNonNull(userHome, "userHome");
    Objects.requireNonNull(explicitConfiguration, "explicitConfiguration");
    ObjectNode merged = readDefaults();
    mergeIfPresent(
        merged,
        userHome
            .resolve(ConfigurationConstants.USER_CONFIGURATION_DIRECTORY)
            .resolve(ConfigurationConstants.CONFIGURATION_FILE_NAME));
    if (explicitConfiguration.isPresent()) {
      mergeRequired(merged, explicitConfiguration.orElseThrow());
    }
    JsonNode settings = merged.path(ConfigurationConstants.JAROSCOPE_KEY);
    return toConfiguration(settings, userHome);
  }

  /** Reads the immutable defaults bundled with the configuration library. */
  private ObjectNode readDefaults() throws IOException {
    try (InputStream defaults = ConfigurationLoader.class.getResourceAsStream(DEFAULTS_RESOURCE)) {
      if (defaults == null) {
        throw new IOException("Missing bundled configuration defaults");
      }
      return (ObjectNode) mapper.readTree(defaults);
    }
  }

  /** Merges a home configuration file when the optional file exists. */
  private void mergeIfPresent(ObjectNode target, Path path) throws IOException {
    if (Files.isRegularFile(path)) {
      mergeRequired(target, path);
    }
  }

  /** Reads and merges a required explicit configuration file. */
  private void mergeRequired(ObjectNode target, Path path) throws IOException {
    try (Reader reader = Files.newBufferedReader(path)) {
      JsonNode override = mapper.readTree(reader);
      if (override == null || !override.isObject()) {
        throw new InvalidConfigurationException("Configuration must contain a YAML object");
      }
      target.setAll((ObjectNode) deepMerge(target, override));
    }
  }

  /** Recursively merges object values while replacing scalar and array values. */
  private JsonNode deepMerge(JsonNode base, JsonNode override) {
    if (base == null || !base.isObject() || !override.isObject()) {
      return override;
    }
    ObjectNode result = (ObjectNode) base.deepCopy();
    for (Map.Entry<String, JsonNode> property : override.properties()) {
      String key = property.getKey();
      JsonNode value = property.getValue();
      result.set(key, deepMerge(result.get(key), value));
    }
    return result;
  }

  /** Converts the merged YAML tree into validated domain configuration. */
  private JaroscopeConfiguration toConfiguration(JsonNode settings, Path userHome) {
    JsonNode cache = settings.path(CACHE_KEY);
    Path cacheDirectory = expandUserHome(cache.path(CACHE_DIRECTORY_KEY).asText(), userHome);
    Duration cacheMaxAge = parseDuration(cache.path(CACHE_MAX_AGE_KEY).asText());
    long cacheMaxSizeBytes = parseBytes(cache.path(CACHE_MAX_SIZE_KEY).asText());
    long maxSourceResponseBytes =
        parseBytes(settings.path(RESPONSE_KEY).path(MAX_SOURCE_SIZE_KEY).asText());
    long maxJarResourceBytes =
        parseBytes(settings.path(RESPONSE_KEY).path(MAX_JAR_RESOURCE_SIZE_KEY).asText());
    JsonNode background = settings.path(BACKGROUND_DECOMPILATION_KEY);
    JsonNode archive = settings.path(ARCHIVE_KEY);
    JsonNode decompiler = settings.path(DECOMPILER_KEY);
    List<Path> bannedRoots = new ArrayList<>();
    Iterator<JsonNode> bannedRootNodes =
        settings.path(SECURITY_KEY).path(BANNED_ROOTS_KEY).elements();
    while (bannedRootNodes.hasNext()) {
      String bannedRoot = bannedRootNodes.next().asText();
      bannedRoots.add(expandUserHome(bannedRoot, userHome).toAbsolutePath().normalize());
    }
    return new JaroscopeConfiguration(
        settings.path(TARGET_RELEASE_KEY).asInt(),
        cacheDirectory,
        cacheMaxAge,
        cacheMaxSizeBytes,
        bannedRoots,
        maxSourceResponseBytes,
        background.path(BACKGROUND_ENABLED_KEY).asBoolean(),
        positiveInteger(
            background.path(MAX_CONCURRENT_BACKGROUND_JARS_KEY).asInt(),
            MAX_CONCURRENT_BACKGROUND_JARS_KEY),
        positiveInteger(
            background.path(MAX_BACKGROUND_CLASSES_KEY).asInt(), MAX_BACKGROUND_CLASSES_KEY),
        parseBytes(archive.path(MAX_ARCHIVE_SIZE_KEY).asText()),
        positiveInteger(archive.path(MAX_ARCHIVE_ENTRIES_KEY).asInt(), MAX_ARCHIVE_ENTRIES_KEY),
        parseBytes(archive.path(MAX_CLASS_SIZE_KEY).asText()),
        parseBytes(archive.path(MAX_EXPANDED_CLASS_SIZE_KEY).asText()),
        positiveInteger(
            decompiler.path(MAX_CONCURRENT_VINEFLOWER_RUNS_KEY).asInt(),
            MAX_CONCURRENT_VINEFLOWER_RUNS_KEY),
        positiveInteger(
            decompiler.path(VINEFLOWER_THREADS_PER_RUN_KEY).asInt(),
            VINEFLOWER_THREADS_PER_RUN_KEY),
        maxJarResourceBytes);
  }

  /** Rejects a non-positive integer configuration value. */
  private int positiveInteger(int value, String key) {
    if (value < 1) {
      throw new InvalidConfigurationException(key + " must be positive");
    }
    return value;
  }

  /** Expands the home-directory shorthand used by the YAML configuration. */
  private Path expandUserHome(String value, Path userHome) {
    if (value.equals("~")) {
      return userHome;
    }
    String prefix = ConfigurationConstants.USER_HOME_PREFIX;
    return value.startsWith(prefix)
        ? userHome.resolve(value.substring(prefix.length()))
        : Path.of(value);
  }

  /** Parses the supported day-based cache age syntax. */
  private Duration parseDuration(String value) {
    if (!value.endsWith(DAYS_SUFFIX)) {
      throw new InvalidConfigurationException("Cache max-age must use a day suffix");
    }
    try {
      long days = Long.parseLong(value.substring(0, value.length() - DAYS_SUFFIX.length()));
      return Duration.ofDays(days);
    } catch (NumberFormatException | ArithmeticException exception) {
      throw new InvalidConfigurationException(
          "Cache max-age must contain a valid day count", exception);
    }
  }

  /** Parses the supported byte-size suffixes into a byte count. */
  private long parseBytes(String value) {
    long multiplier;
    String suffix;
    if (value.endsWith(GIBIBYTES_SUFFIX)) {
      multiplier = BYTES_PER_GIBIBYTE;
      suffix = GIBIBYTES_SUFFIX;
    } else if (value.endsWith(MEBIBYTES_SUFFIX)) {
      multiplier = BYTES_PER_MEBIBYTE;
      suffix = MEBIBYTES_SUFFIX;
    } else if (value.endsWith(KIBIBYTES_SUFFIX)) {
      multiplier = BYTES_PER_KIBIBYTE;
      suffix = KIBIBYTES_SUFFIX;
    } else if (value.endsWith(BYTES_SUFFIX)) {
      multiplier = 1L;
      suffix = BYTES_SUFFIX;
    } else {
      throw new InvalidConfigurationException("Cache max-size must use a byte suffix");
    }
    String number = value.substring(0, value.length() - suffix.length());
    try {
      return Math.multiplyExact(Long.parseLong(number), multiplier);
    } catch (NumberFormatException | ArithmeticException exception) {
      throw new InvalidConfigurationException(
          "Cache max-size must contain a valid byte count", exception);
    }
  }
}
