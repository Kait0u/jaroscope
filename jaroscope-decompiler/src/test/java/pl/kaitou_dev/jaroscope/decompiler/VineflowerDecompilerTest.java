package pl.kaitou_dev.jaroscope.decompiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

  /** Confirms that one batch invocation emits multiple requested classes. */
  @Test
  void decompilesMultipleClassesInOneBatch() throws Exception {
    List<DecompiledClass> sources = new ArrayList<>();

    Set<String> emitted =
        new VineflowerDecompiler()
            .decompileClasses(
                fixture(), List.of("example.Greeter", "example.Helper"), 21, sources::add);

    assertEquals(Set.of("example.Greeter", "example.Helper"), emitted);
    assertEquals(2, sources.size());
  }

  /** Confirms that selected class bytes follow the requested multi-release target. */
  @Test
  void usesTargetReleaseSpecificImplementation() throws Exception {
    VineflowerDecompiler decompiler = new VineflowerDecompiler();

    DecompiledClass olderRelease = decompiler.decompile(fixture(), "example.Greeter", 11);
    DecompiledClass newerRelease = decompiler.decompile(fixture(), "example.Greeter", 21);

    assertTrue(olderRelease.source().contains("return \"base\""));
    assertTrue(newerRelease.source().contains("return \"java17\""));
  }
}
