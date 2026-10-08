package pl.kaitou_dev.jaroscope.core;

/** Configurable archive and class-file resource limits for JAR inspection. */
public record ArchiveLimits(
    long maxArchiveBytes, int maxEntries, long maxClassFileBytes, long maxExpandedClassBytes) {
  /** Creates limits and rejects values that cannot be safely enforced. */
  public ArchiveLimits {
    if (maxArchiveBytes < 1L
        || maxEntries < 1
        || maxClassFileBytes < 1L
        || maxClassFileBytes >= Integer.MAX_VALUE
        || maxExpandedClassBytes < maxClassFileBytes) {
      throw new JarIndexException("Archive limits must be positive and internally consistent");
    }
  }

  /** Returns the standard safe defaults for local JAR analysis. */
  public static ArchiveLimits defaults() {
    return new ArchiveLimits(
        JarFormat.DEFAULT_MAX_ARCHIVE_BYTES,
        JarFormat.DEFAULT_MAX_ARCHIVE_ENTRIES,
        JarFormat.DEFAULT_MAX_CLASS_FILE_BYTES,
        JarFormat.DEFAULT_MAX_EXPANDED_CLASS_BYTES);
  }
}
