package pl.kaitou_dev.jaroscope.config;

/** Configuration keys and default values shared by configuration components. */
public final class ConfigurationConstants {
  /** The top-level YAML key for JARoscope settings. */
  public static final String JAROSCOPE_KEY = "jaroscope";

  /** The default Java release used for multi-release JAR selection. */
  public static final int DEFAULT_TARGET_RELEASE = 21;

  /** The default cache retention period in days. */
  public static final long DEFAULT_CACHE_MAX_AGE_DAYS = 30;

  /** The default cache size limit in bytes. */
  public static final long DEFAULT_CACHE_MAX_SIZE_BYTES = 2L * 1024L * 1024L * 1024L;

  /** The default maximum UTF-8 source size returned in one MCP response. */
  public static final long DEFAULT_MAX_SOURCE_RESPONSE_BYTES = 1024L * 1024L;

  /** The relative directory used for the user's JARoscope configuration. */
  public static final String USER_CONFIGURATION_DIRECTORY = ".jaroscope";

  /** The configuration file name. */
  public static final String CONFIGURATION_FILE_NAME = "application.yml";

  /** The prefix used for paths relative to the user's home directory. */
  public static final String USER_HOME_PREFIX = "~/";

  private ConfigurationConstants() {
    throw new AssertionError("No instances");
  }
}
