package pl.kaitou_dev.jaroscope.config;

import pl.kaitou_dev.jaroscope.core.JarFormat;

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

  /** The default maximum raw entry size returned by one JAR resource request. */
  public static final long DEFAULT_MAX_JAR_RESOURCE_BYTES = 1024L * 1024L;

  /** The default number of JARs decompiled concurrently in the background. */
  public static final int DEFAULT_MAX_BACKGROUND_JARS = 2;

  /** The default maximum number of classes warmed per background JAR job. */
  public static final int DEFAULT_MAX_BACKGROUND_CLASSES = 10_000;

  /** Default maximum compressed JAR size. */
  public static final long DEFAULT_MAX_ARCHIVE_BYTES = JarFormat.DEFAULT_MAX_ARCHIVE_BYTES;

  /** Default maximum number of archive entries. */
  public static final int DEFAULT_MAX_ARCHIVE_ENTRIES = JarFormat.DEFAULT_MAX_ARCHIVE_ENTRIES;

  /** Default maximum uncompressed class entry size. */
  public static final long DEFAULT_MAX_CLASS_FILE_BYTES = JarFormat.DEFAULT_MAX_CLASS_FILE_BYTES;

  /** Default maximum aggregate class bytes staged for Vineflower. */
  public static final long DEFAULT_MAX_EXPANDED_CLASS_BYTES =
      JarFormat.DEFAULT_MAX_EXPANDED_CLASS_BYTES;

  /** Default maximum number of simultaneous Vineflower runs. */
  public static final int DEFAULT_MAX_CONCURRENT_VINEFLOWER_RUNS = 2;

  /** Default number of Vineflower worker threads per run. */
  public static final int DEFAULT_VINEFLOWER_THREADS_PER_RUN = 2;

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
