package pl.kaitou_dev.jaroscope.decompiler;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Verifies source extraction through Vineflower using the bundled JAR fixture. */
class VineflowerDecompilerTest {
  /** Locates the committed fixture JAR. */
  private static Path fixture() throws URISyntaxException {
    return Path.of(
        VineflowerDecompilerTest.class.getResource("/fixtures/multi-release.jar").toURI());
  }

  /** Confirms that the selected class produces readable Java source. */
  @Test
  void decompilesSelectedClass() throws Exception {
    DecompiledClass result = new VineflowerDecompiler().decompile(fixture(), "example.Greeter", 21);

    assertTrue(result.source().contains("class Greeter"));
    assertTrue(result.source().contains("greeting"));
  }
}
