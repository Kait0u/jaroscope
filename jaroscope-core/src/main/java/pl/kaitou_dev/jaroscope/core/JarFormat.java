package pl.kaitou_dev.jaroscope.core;

/** Entry-name and version conventions shared by Java JAR inspection operations. */
public final class JarFormat {
  /** The suffix of a compiled Java class entry in a JAR. */
  public static final String CLASS_FILE_SUFFIX = ".class";

  /** The prefix under which a multi-release JAR stores release-specific entries. */
  public static final String VERSIONED_ENTRY_PREFIX = "META-INF/versions/";

  /** The first Java release for which multi-release JAR entries are valid. */
  public static final int FIRST_MULTI_RELEASE_VERSION = 9;

  /** Prevents instances of this constants-only type. */
  private JarFormat() {
    throw new AssertionError("No instances");
  }
}
