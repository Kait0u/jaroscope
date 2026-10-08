package pl.kaitou_dev.jaroscope.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Maps binary class names to their archive entries as seen by a given Java release.
 *
 * <p>Indexing reads JAR metadata only: it neither loads classes nor decompiles bytecode. The
 * returned index is immutable and does not retain an open archive handle.
 */
public final class JarIndex {
  /** The lowest supported target release for inspecting an archive. */
  private static final int MINIMUM_TARGET_RELEASE = 1;

  private final Map<String, String> entries;

  /** Stores a completed class index without exposing its mutable construction map. */
  private JarIndex(Map<String, String> entries) {
    this.entries = Collections.unmodifiableMap(entries);
  }

  /**
   * Indexes the classes visible at the requested Java release without loading them.
   *
   * @param path the readable local JAR to inspect
   * @param targetRelease the Java release whose class selection rules should apply
   * @return an immutable index mapping binary class names to entry names
   * @throws IOException if the JAR cannot be opened or read
   * @throws JarIndexException if the target release is not positive
   */
  public static JarIndex open(Path path, int targetRelease) throws IOException {
    return open(path, targetRelease, ArchiveLimits.defaults());
  }

  /**
   * Indexes classes using explicit archive resource limits.
   *
   * @param path the readable local JAR to inspect
   * @param targetRelease the Java release whose class selection rules should apply
   * @param limits compressed-size, entry-count, and class-size limits
   * @return an immutable index mapping binary class names to entry names
   * @throws IOException if the JAR cannot be opened or read
   * @throws JarIndexException if the release or archive limits are violated
   */
  public static JarIndex open(Path path, int targetRelease, ArchiveLimits limits)
      throws IOException {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(limits, "limits");
    if (targetRelease < MINIMUM_TARGET_RELEASE) {
      throw new JarIndexException("targetRelease must be positive");
    }
    if (Files.size(path) > limits.maxArchiveBytes()) {
      throw new JarIndexException("JAR exceeds the configured archive size limit");
    }

    Map<String, String> entries = new TreeMap<>();
    Map<String, Integer> versions = new TreeMap<>();
    try (JarFile jar = new JarFile(path.toFile(), false, JarFile.OPEN_READ)) {
      boolean multiRelease = jar.isMultiRelease();
      Enumeration<JarEntry> jarEntries = jar.entries();
      int entryCount = 0;
      while (jarEntries.hasMoreElements()) {
        JarEntry entry = jarEntries.nextElement();
        ++entryCount;
        if (entryCount > limits.maxEntries()) {
          throw new JarIndexException("JAR exceeds the configured entry-count limit");
        }
        if (entry.isDirectory()) {
          continue;
        }

        String name = entry.getName();
        int version = 0;
        String classPath = name;
        if (name.startsWith(JarFormat.VERSIONED_ENTRY_PREFIX)) {
          if (!multiRelease) {
            continue;
          }
          int slash = name.indexOf('/', JarFormat.VERSIONED_ENTRY_PREFIX.length());
          if (slash < 0) {
            continue;
          }
          try {
            version =
                Integer.parseInt(name.substring(JarFormat.VERSIONED_ENTRY_PREFIX.length(), slash));
          } catch (NumberFormatException ignored) {
            continue;
          }
          if (version < JarFormat.FIRST_MULTI_RELEASE_VERSION || version > targetRelease) {
            continue;
          }
          classPath = name.substring(slash + 1);
        }
        if (!classPath.endsWith(JarFormat.CLASS_FILE_SUFFIX)
            || classPath.length() <= JarFormat.CLASS_FILE_SUFFIX.length()) {
          continue;
        }
        if (entry.getSize() > limits.maxClassFileBytes()) {
          throw new JarIndexException(
              "Class entry exceeds the configured class-size limit: " + name);
        }

        String className =
            classPath
                .substring(0, classPath.length() - JarFormat.CLASS_FILE_SUFFIX.length())
                .replace('/', '.');
        Integer previousVersion = versions.get(className);
        if (previousVersion == null || version >= previousVersion) {
          entries.put(className, name);
          versions.put(className, version);
        }
      }
    }
    return new JarIndex(entries);
  }

  /**
   * Returns binary class names and their selected archive entries in class-name order.
   *
   * @return an immutable map from binary class names to entry names
   */
  public Map<String, String> classes() {
    return entries;
  }

  /**
   * Finds the selected archive entry for a binary class name.
   *
   * @param binaryName a binary class name such as {@code java.lang.String}
   * @return the archive entry, or empty when the class is absent
   */
  public Optional<String> entryFor(String binaryName) {
    return Optional.ofNullable(entries.get(binaryName));
  }
}
