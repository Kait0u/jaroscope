package pl.kaitou_dev.jaroscope.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Validates local JAR paths against the configured banned roots. */
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
   * @throws SecurityException if the path is under a banned root
   */
  public Path validate(Path requestedPath) throws IOException {
    Path realPath = requestedPath.toRealPath();
    if (!Files.isRegularFile(realPath)
        || !realPath.getFileName().toString().endsWith(JAR_FILE_SUFFIX)) {
      throw new IOException("Path is not a regular JAR file: " + requestedPath);
    }
    for (Path bannedRoot : configuration.bannedRoots()) {
      if (realPath.startsWith(bannedRoot)) {
        throw new SecurityException("JAR path is under a banned root: " + requestedPath);
      }
    }
    return realPath;
  }
}
