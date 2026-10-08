package pl.kaitou_dev.jaroscope.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

/** Validates local JAR paths against the configured banned roots after resolving symlinks. */
public final class JarPathPolicy {
  /** The required suffix for paths accepted as JAR files. */
  private static final String JAR_FILE_SUFFIX = ".jar";

  private final JaroscopeConfiguration configuration;

  /** Creates a path policy backed by validated configuration. */
  public JarPathPolicy(JaroscopeConfiguration configuration) {
    this.configuration = Objects.requireNonNull(configuration, "configuration");
  }

  /**
   * Resolves and validates a readable regular JAR file.
   *
   * @param requestedPath the path supplied by a caller
   * @return the real path to the permitted JAR
   * @throws IOException if the path is missing, inaccessible, or not a regular file
   * @throws JarPathException if the path is not a permitted JAR
   */
  public Path validate(Path requestedPath) throws IOException {
    Path realPath = requestedPath.toRealPath();
    if (!Files.isRegularFile(realPath)
        || !realPath.getFileName().toString().endsWith(JAR_FILE_SUFFIX)) {
      throw new JarPathException("Path is not a regular JAR file: " + requestedPath);
    }
    for (Path bannedRoot : configuration.bannedRoots()) {
      Path realBannedRoot = resolveExistingAncestor(bannedRoot);
      if (realPath.startsWith(realBannedRoot)) {
        throw new JarPathException("JAR path is under a banned root: " + requestedPath);
      }
    }
    return realPath;
  }

  /** Resolves symlinks in an existing ancestor while preserving a not-yet-existing suffix. */
  private Path resolveExistingAncestor(Path path) throws IOException {
    Path normalizedPath = path.toAbsolutePath().normalize();
    Path existingAncestor = normalizedPath;
    ArrayList<Path> missingSuffix = new ArrayList<>();
    while (existingAncestor != null && !Files.exists(existingAncestor)) {
      Path fileName = existingAncestor.getFileName();
      if (fileName != null) {
        missingSuffix.add(fileName);
      }
      existingAncestor = existingAncestor.getParent();
    }
    if (existingAncestor == null) {
      return normalizedPath;
    }
    Path resolvedPath = existingAncestor.toRealPath();
    Collections.reverse(missingSuffix);
    for (Path suffixElement : missingSuffix) {
      resolvedPath = resolvedPath.resolve(suffixElement);
    }
    return resolvedPath.normalize();
  }
}
