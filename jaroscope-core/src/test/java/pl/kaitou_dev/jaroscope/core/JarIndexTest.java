package pl.kaitou_dev.jaroscope.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Exercises class selection against a committed multi-release JAR fixture. */
class JarIndexTest {
  /** Locates the prebundled fixture rather than creating a synthetic archive during the test. */
  private static Path fixture() throws URISyntaxException {
    return Path.of(JarIndexTest.class.getResource("/fixtures/multi-release.jar").toURI());
  }

  /** Checks that releases below the versioned entry see the JAR's base implementation. */
  @Test
  void selectsBaseClassForOlderReleases() throws Exception {
    JarIndex index = JarIndex.open(fixture(), 11);

    assertEquals("example/Greeter.class", index.entryFor("example.Greeter").orElseThrow());
    assertTrue(index.classes().containsKey("example.Helper"));
    assertFalse(index.classes().containsKey("example.Missing"));
  }

  /** Checks that a newer runtime sees the highest compatible versioned entry. */
  @Test
  void selectsHighestSupportedVersion() throws Exception {
    JarIndex index = JarIndex.open(fixture(), 21);

    assertEquals(
        "META-INF/versions/17/example/Greeter.class",
        index.entryFor("example.Greeter").orElseThrow());
    assertEquals(3, index.classes().size());
  }

  /** Checks that nonsensical Java release numbers fail before reading the archive. */
  @Test
  void rejectsInvalidTargetRelease() throws Exception {
    assertThrows(JarIndexException.class, () -> JarIndex.open(fixture(), 0));
  }
}
