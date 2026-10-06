package pl.kaitou_dev.jaroscope.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class JarIndexTest {
  private static Path fixture() throws URISyntaxException {
    return Path.of(JarIndexTest.class.getResource("/fixtures/multi-release.jar").toURI());
  }

  @Test
  void selectsBaseClassForOlderReleases() throws Exception {
    JarIndex index = JarIndex.open(fixture(), 11);

    assertEquals("example/Greeter.class", index.entryFor("example.Greeter").orElseThrow());
    assertTrue(index.classes().containsKey("example.Helper"));
    assertFalse(index.classes().containsKey("example.Missing"));
  }

  @Test
  void selectsHighestSupportedVersion() throws Exception {
    JarIndex index = JarIndex.open(fixture(), 21);

    assertEquals(
        "META-INF/versions/17/example/Greeter.class",
        index.entryFor("example.Greeter").orElseThrow());
    assertEquals(2, index.classes().size());
  }

  @Test
  void rejectsInvalidTargetRelease() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> JarIndex.open(fixture(), 0));
  }
}
