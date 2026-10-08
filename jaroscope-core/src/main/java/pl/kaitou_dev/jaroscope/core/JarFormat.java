package pl.kaitou_dev.jaroscope.core;

/** Entry-name and version conventions shared by Java JAR inspection operations. */
public final class JarFormat {
  /** The suffix of a compiled Java class entry in a JAR. */
  public static final String CLASS_FILE_SUFFIX = ".class";

  /** The prefix under which a multi-release JAR stores release-specific entries. */
  public static final String VERSIONED_ENTRY_PREFIX = "META-INF/versions/";

  /** The first Java release for which multi-release JAR entries are valid. */
  public static final int FIRST_MULTI_RELEASE_VERSION = 9;

  /** Default maximum compressed archive size accepted for inspection. */
  public static final long DEFAULT_MAX_ARCHIVE_BYTES = 1024L * 1024L * 1024L;

  /** Default maximum number of central-directory entries accepted. */
  public static final int DEFAULT_MAX_ARCHIVE_ENTRIES = 100_000;

  /** Default maximum uncompressed size of one class entry. */
  public static final long DEFAULT_MAX_CLASS_FILE_BYTES = 64L * 1024L * 1024L;

  /** Default maximum sum of selected class bytes staged for one Vineflower call. */
  public static final long DEFAULT_MAX_EXPANDED_CLASS_BYTES = 2L * 1024L * 1024L * 1024L;

  /** Prevents instances of this constants-only type. */
  private JarFormat() {
    throw new AssertionError("No instances");
  }
}
