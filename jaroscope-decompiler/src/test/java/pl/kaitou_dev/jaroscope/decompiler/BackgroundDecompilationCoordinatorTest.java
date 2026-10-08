package pl.kaitou_dev.jaroscope.decompiler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.kaitou_dev.jaroscope.config.JaroscopeConfiguration;

/** Verifies scheduling limits and duplicate job coalescing. */
class BackgroundDecompilationCoordinatorTest {
  @TempDir Path temporaryDirectory;

  /** Locates the committed multi-release fixture JAR. */
  private static Path fixture() throws URISyntaxException {
    return Path.of(
        BackgroundDecompilationCoordinatorTest.class
            .getResource("/fixtures/multi-release.jar")
            .toURI());
  }

  /** Confirms that repeated requests for the same active JAR and release coalesce. */
  @Test
  void coalescesDuplicateJobsUntilTheActiveJobFinishes() throws Exception {
    CountDownLatch decompilationStarted = new CountDownLatch(1);
    CountDownLatch releaseDecompilation = new CountDownLatch(1);
    Decompiler blockingDecompiler =
        (jarPath, binaryName, targetRelease) -> {
          decompilationStarted.countDown();
          try {
            releaseDecompilation.await();
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DecompilerException("Test decompilation interrupted", exception);
          }
          return new DecompiledClass(binaryName, targetRelease, "source");
        };
    JaroscopeConfiguration configuration =
        new JaroscopeConfiguration(
            21,
            temporaryDirectory.resolve("cache"),
            Duration.ofDays(30),
            1024L,
            List.of(),
            1024L,
            true,
            1,
            1);
    BackgroundDecompilationCoordinator coordinator =
        new BackgroundDecompilationCoordinator(configuration, blockingDecompiler);
    try {
      Path jar = fixture();
      assertTrue(coordinator.schedule(jar, 21));
      assertTrue(decompilationStarted.await(5, TimeUnit.SECONDS));
      assertFalse(coordinator.schedule(jar, 21));
    } finally {
      releaseDecompilation.countDown();
      coordinator.close();
    }
  }
}
