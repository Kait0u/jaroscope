package pl.kaitou_dev.jaroscope.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies JAR path validation against configured banned roots. */
class JarPathPolicyTest {
  @TempDir Path temporaryDirectory;

  /** Confirms that an existing JAR outside banned roots is accepted. */
  @Test
  void acceptsJarOutsideBannedRoots() throws Exception {
    Path jar = Files.createFile(temporaryDirectory.resolve("library.jar"));
    JarPathPolicy policy = new JarPathPolicy(configuration(List.of()));

    assertEquals(jar.toRealPath(), policy.validate(jar));
  }

  /** Confirms that a JAR below a banned root is rejected. */
  @Test
  void rejectsJarUnderBannedRoot() throws Exception {
    Path bannedRoot = Files.createDirectory(temporaryDirectory.resolve("banned"));
    Path jar = Files.createFile(bannedRoot.resolve("library.jar"));
    JarPathPolicy policy = new JarPathPolicy(configuration(List.of(bannedRoot)));

    assertThrows(SecurityException.class, () -> policy.validate(jar));
  }

  /** Creates the minimum configuration needed by path policy tests. */
  private JaroscopeConfiguration configuration(List<Path> bannedRoots) {
    return new JaroscopeConfiguration(
        21, temporaryDirectory, Duration.ofDays(30), 2L * 1024L, bannedRoots);
  }
}
