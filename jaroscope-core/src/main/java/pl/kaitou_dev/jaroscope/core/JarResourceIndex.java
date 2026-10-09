package pl.kaitou_dev.jaroscope.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

/** Indexes non-class JAR entries with multi-release selection and bounded reads. */
public final class JarResourceIndex {
  /** Prefix for versioned entries in a multi-release JAR. */
  private static final String VERSIONED_ENTRY_PREFIX = "META-INF/versions/";

  /** Class-file suffix excluded from resource discovery. */
  private static final String CLASS_FILE_SUFFIX = ".class";

  /** Directory-entry suffix excluded from resource discovery. */
  private static final String DIRECTORY_SUFFIX = "/";

  /** Lowest Java release that can contain multi-release entries. */
  private static final int MINIMUM_TARGET_RELEASE = 1;

  private final Path jarPath;
  private final int targetRelease;
  private final Map<String, JarResource> resources;
  private final Map<String, String> archiveEntries;

  private JarResourceIndex(
      Path jarPath,
      int targetRelease,
      Map<String, JarResource> resources,
      Map<String, String> archiveEntries) {
    this.jarPath = jarPath;
    this.targetRelease = targetRelease;
    this.resources = Collections.unmodifiableMap(resources);
    this.archiveEntries = Collections.unmodifiableMap(archiveEntries);
  }

  /**
   * Indexes visible non-class resources using explicit archive limits.
   *
   * @param jarPath the readable JAR to inspect
   * @param targetRelease the release for multi-release resource selection
   * @param limits the configured archive limits
   * @return an immutable, name-sorted index
   * @throws IOException if the JAR cannot be read
   * @throws JarIndexException if the JAR exceeds configured archive limits
   */
  public static JarResourceIndex open(Path jarPath, int targetRelease, ArchiveLimits limits)
      throws IOException {
    Objects.requireNonNull(jarPath, "jarPath");
    Objects.requireNonNull(limits, "limits");
    if (targetRelease < MINIMUM_TARGET_RELEASE) {
      throw new JarIndexException("targetRelease must be positive");
    }
    if (Files.size(jarPath) > limits.maxArchiveBytes()) {
      throw new JarIndexException("JAR exceeds the configured archive size limit");
    }

    Map<String, JarResource> resources = new TreeMap<>();
    Map<String, String> archiveEntries = new TreeMap<>();
    try (JarFile jar = new JarFile(jarPath.toFile(), false, JarFile.OPEN_READ)) {
      boolean multiRelease = jar.isMultiRelease();
      Enumeration<JarEntry> entries = jar.entries();
      int entryCount = 0;
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        ++entryCount;
        if (entryCount > limits.maxEntries()) {
          throw new JarIndexException("JAR exceeds the configured entry-count limit");
        }
        if (entry.isDirectory()) {
          continue;
        }

        String archiveName = entry.getName();
        String resourceName = archiveName;
        int version = 0;
        if (archiveName.startsWith(VERSIONED_ENTRY_PREFIX)) {
          if (!multiRelease) {
            continue;
          }
          int versionSeparator = archiveName.indexOf('/', VERSIONED_ENTRY_PREFIX.length());
          if (versionSeparator < 0) {
            continue;
          }
          try {
            version =
                Integer.parseInt(
                    archiveName.substring(VERSIONED_ENTRY_PREFIX.length(), versionSeparator));
          } catch (NumberFormatException ignored) {
            continue;
          }
          if (version < JarFormat.FIRST_MULTI_RELEASE_VERSION || version > targetRelease) {
            continue;
          }
          resourceName = archiveName.substring(versionSeparator + 1);
        }
        if (resourceName.isBlank()
            || resourceName.endsWith(DIRECTORY_SUFFIX)
            || resourceName.endsWith(CLASS_FILE_SUFFIX)) {
          continue;
        }
        JarResource current = resources.get(resourceName);
        if (current == null || version >= current.selectedVersion()) {
          resources.put(resourceName, new JarResource(resourceName, version, entry.getSize()));
          archiveEntries.put(resourceName, archiveName);
        }
      }
    }
    return new JarResourceIndex(jarPath, targetRelease, resources, archiveEntries);
  }

  /** Returns all selected non-class resources in name order. */
  public Map<String, JarResource> resources() {
    return resources;
  }

  /** Finds one selected resource by its logical archive name. */
  public Optional<JarResource> resource(String resourceName) {
    return Optional.ofNullable(resources.get(resourceName));
  }

  /**
   * Reads one selected resource while enforcing the configured raw byte limit.
   *
   * @param resourceName logical JAR entry name
   * @param maxBytes maximum uncompressed resource size
   * @return resource bytes
   * @throws IOException if the JAR entry cannot be read
   * @throws JarResourceException if the resource is absent or too large
   */
  public byte[] read(String resourceName, long maxBytes) throws IOException {
    JarResource resource =
        resource(resourceName)
            .orElseThrow(() -> new JarResourceException("Resource not found: " + resourceName));
    if (maxBytes < 1L || resource.sizeBytes() > maxBytes) {
      throw new JarResourceException("Resource exceeds the configured read limit: " + resourceName);
    }
    String archiveName = archiveEntries.get(resourceName);
    try (JarFile jar = new JarFile(jarPath.toFile(), false, JarFile.OPEN_READ)) {
      JarEntry entry = jar.getJarEntry(archiveName);
      if (entry == null) {
        throw new IOException("Selected resource entry disappeared: " + archiveName);
      }
      try (InputStream input = jar.getInputStream(entry);
          ByteArrayOutputStream output = new ByteArrayOutputStream()) {
        byte[] buffer = new byte[8192];
        long totalBytes = 0L;
        int bytesRead;
        while ((bytesRead = input.read(buffer)) >= 0) {
          totalBytes += bytesRead;
          if (totalBytes > maxBytes) {
            throw new JarResourceException(
                "Resource exceeds the configured read limit: " + resourceName);
          }
          output.write(buffer, 0, bytesRead);
        }
        return output.toByteArray();
      }
    }
  }

  /** Returns the configured release used to select this resource view. */
  public int targetRelease() {
    return targetRelease;
  }
}
