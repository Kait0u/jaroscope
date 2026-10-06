package pl.kaitou_dev.jaroscope.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/** An index of the classes visible at a given Java release in one JAR. */
public final class JarIndex {
  private static final String VERSION_PREFIX = "META-INF/versions/";

  private final Map<String, String> entries;

  private JarIndex(Map<String, String> entries) {
    this.entries = Collections.unmodifiableMap(entries);
  }

  /** Indexes a JAR without loading or executing any of its classes. */
  public static JarIndex open(Path path, int targetRelease) throws IOException {
    if (targetRelease < 1) {
      throw new IllegalArgumentException("targetRelease must be positive");
    }

    Map<String, String> entries = new TreeMap<>();
    Map<String, Integer> versions = new TreeMap<>();
    try (JarFile jar = new JarFile(path.toFile(), false, JarFile.OPEN_READ)) {
      Manifest manifest = jar.getManifest();
      boolean multiRelease =
          manifest != null
              && Boolean.parseBoolean(manifest.getMainAttributes().getValue("Multi-Release"));
      Enumeration<JarEntry> jarEntries = jar.entries();
      while (jarEntries.hasMoreElements()) {
        JarEntry entry = jarEntries.nextElement();
        if (entry.isDirectory()) {
          continue;
        }

        String name = entry.getName();
        int version = 0;
        String classPath = name;
        if (name.startsWith(VERSION_PREFIX)) {
          if (!multiRelease) {
            continue;
          }
          int slash = name.indexOf('/', VERSION_PREFIX.length());
          if (slash < 0) {
            continue;
          }
          try {
            version = Integer.parseInt(name.substring(VERSION_PREFIX.length(), slash));
          } catch (NumberFormatException ignored) {
            continue;
          }
          if (version < 9 || version > targetRelease) {
            continue;
          }
          classPath = name.substring(slash + 1);
        }
        if (!classPath.endsWith(".class") || classPath.length() <= ".class".length()) {
          continue;
        }

        String className =
            classPath.substring(0, classPath.length() - ".class".length()).replace('/', '.');
        if (version >= versions.getOrDefault(className, -1)) {
          entries.put(className, name);
          versions.put(className, version);
        }
      }
    }
    return new JarIndex(entries);
  }

  /** Binary class name to its selected archive entry, in name order. */
  public Map<String, String> classes() {
    return entries;
  }

  public Optional<String> entryFor(String binaryName) {
    return Optional.ofNullable(entries.get(binaryName));
  }
}
