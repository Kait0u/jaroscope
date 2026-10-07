package pl.kaitou_dev.jaroscope.decompiler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.kaitou_dev.jaroscope.cache.CacheStore;
import pl.kaitou_dev.jaroscope.config.JaroscopeConfiguration;

/** Verifies that repeated decompilation requests reuse cached source. */
class CachingDecompilerTest {
  @TempDir Path temporaryDirectory;

  /** Confirms that the delegate runs once for two identical requests. */
  @Test
  void reusesCachedSource() throws Exception {
    Path jar = Files.createFile(temporaryDirectory.resolve("example.jar"));
    AtomicInteger calls = new AtomicInteger();
    Decompiler delegate =
        (path, binaryName, targetRelease) -> {
          calls.incrementAndGet();
          return new DecompiledClass(binaryName, targetRelease, "source");
        };
    CacheStore cache =
        new CacheStore(
            new JaroscopeConfiguration(
                21, temporaryDirectory.resolve("cache"), Duration.ofDays(30), 1024L, List.of()));
    CachingDecompiler decompiler = new CachingDecompiler(delegate, cache);

    decompiler.decompile(jar, "example.Greeter", 21);
    DecompiledClass result = decompiler.decompile(jar, "example.Greeter", 21);

    assertEquals("source", result.source());
    assertEquals(1, calls.get());
  }
}
