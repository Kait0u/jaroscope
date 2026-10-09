package pl.kaitou_dev.jaroscope.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Verifies resource indexing and reading against the prebundled multi-release fixture. */
class JarResourceIndexTest {
  private static final String CONFIG_RESOURCE = "example/config.properties";
  private static final String BINARY_RESOURCE = "example/binary.dat";

  /** Locates the committed multi-release fixture JAR. */
  private static Path fixture() throws URISyntaxException {
    return Path.of(JarResourceIndexTest.class.getResource("/fixtures/multi-release.jar").toURI());
  }

  /** Confirms that non-class resource selection follows the requested Java release. */
  @Test
  void selectsReleaseSpecificResourceEntries() throws Exception {
    JarResourceIndex java11 = JarResourceIndex.open(fixture(), 11, ArchiveLimits.defaults());
    JarResourceIndex java21 = JarResourceIndex.open(fixture(), 21, ArchiveLimits.defaults());

    assertEquals(
        "generation=base\n",
        new String(java11.read(CONFIG_RESOURCE, 1024L), StandardCharsets.UTF_8));
    assertEquals(
        "generation=java17\n",
        new String(java21.read(CONFIG_RESOURCE, 1024L), StandardCharsets.UTF_8));
    assertFalse(java21.resources().containsKey("example.Greeter.class"));
    assertTrue(java21.resources().containsKey("META-INF/services/example.GreetingProvider"));
  }

  /** Confirms that binary resource bytes are returned without text conversion. */
  @Test
  void readsBinaryResourceBytes() throws Exception {
    JarResourceIndex index = JarResourceIndex.open(fixture(), 21, ArchiveLimits.defaults());

    assertArrayEquals(new byte[] {1, 2, (byte) 255}, index.read(BINARY_RESOURCE, 1024L));
  }

  /** Confirms that resource reads enforce their response byte limit. */
  @Test
  void rejectsResourceOverReadLimit() throws Exception {
    JarResourceIndex index = JarResourceIndex.open(fixture(), 21, ArchiveLimits.defaults());

    assertThrows(JarResourceException.class, () -> index.read(BINARY_RESOURCE, 2L));
  }
}
