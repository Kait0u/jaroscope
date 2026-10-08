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

    assertThrows(JarPathException.class, () -> policy.validate(jar));
  }

  /** Confirms that a configured banned-root symlink cannot be bypassed through its target. */
  @Test
  void rejectsJarUnderSymlinkedBannedRoot() throws Exception {
    Path bannedTarget = Files.createDirectory(temporaryDirectory.resolve("restricted"));
    Path bannedAlias = temporaryDirectory.resolve("restricted-alias");
    Files.createSymbolicLink(bannedAlias, bannedTarget);
    Path jar = Files.createFile(bannedTarget.resolve("library.jar"));
    JarPathPolicy policy = new JarPathPolicy(configuration(List.of(bannedAlias)));

    assertThrows(JarPathException.class, () -> policy.validate(jar));
  }

  /** Confirms that a symlinked JAR path is checked by its resolved target. */
  @Test
  void rejectsSymlinkToJarUnderBannedRoot() throws Exception {
    Path bannedRoot = Files.createDirectory(temporaryDirectory.resolve("restricted"));
    Path jar = Files.createFile(bannedRoot.resolve("library.jar"));
    Path alias = temporaryDirectory.resolve("external.jar");
    Files.createSymbolicLink(alias, jar);
    JarPathPolicy policy = new JarPathPolicy(configuration(List.of(bannedRoot)));

    assertThrows(JarPathException.class, () -> policy.validate(alias));
  }

  /** Creates the minimum configuration needed by path policy tests. */
  private JaroscopeConfiguration configuration(List<Path> bannedRoots) {
    return new JaroscopeConfiguration(
        21, temporaryDirectory, Duration.ofDays(30), 2L * 1024L, bannedRoots);
  }
}
