package pl.kaitou_dev.jaroscope.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.kaitou_dev.jaroscope.config.JaroscopeConfiguration;

/** Verifies cache identity, reuse, and cleanup behavior. */
class CacheStoreTest {
  private static final String BINARY_NAME = "example.Greeter";
  private static final String ENGINE_VERSION = "1.12.0";
  private static final String OPTIONS = "default";

  @TempDir Path temporaryDirectory;

  /** Confirms that an exact request can be read after being stored. */
  @Test
  void storesAndReadsExactRequest() throws Exception {
    Path jar = Files.createFile(temporaryDirectory.resolve("example.jar"));
    CacheStore store = new CacheStore(configuration(Duration.ofDays(30), 1024L));

    store.put(jar, BINARY_NAME, 21, ENGINE_VERSION, OPTIONS, "class Greeter {}");

    assertEquals(
        Optional.of("class Greeter {}"), store.get(jar, BINARY_NAME, 21, ENGINE_VERSION, OPTIONS));
    assertEquals(1, sourceEntries().size());
  }

  /** Confirms that changing the class or release produces a different cache entry. */
  @Test
  void separatesRequestIdentity() throws Exception {
    Path jar = Files.createFile(temporaryDirectory.resolve("example.jar"));
    CacheStore store = new CacheStore(configuration(Duration.ofDays(30), 1024L));

    store.put(jar, BINARY_NAME, 21, ENGINE_VERSION, OPTIONS, "source");

    assertTrue(store.get(jar, BINARY_NAME, 17, ENGINE_VERSION, OPTIONS).isEmpty());
  }

  /** Confirms that entries older than the configured age are removed. */
  @Test
  void removesExpiredEntries() throws Exception {
    Path jar = Files.createFile(temporaryDirectory.resolve("example.jar"));
    CacheStore store = new CacheStore(configuration(Duration.ofSeconds(1), 1024L));
    store.put(jar, BINARY_NAME, 21, ENGINE_VERSION, OPTIONS, "source");
    Path entry = sourceEntries().get(0);
    Files.setLastModifiedTime(entry, java.nio.file.attribute.FileTime.fromMillis(0L));

    store.cleanup();

    assertTrue(sourceEntries().isEmpty());
  }

  /** Confirms that least-recently-used entries are removed when the size limit is exceeded. */
  @Test
  void removesOldestEntriesWhenOversized() throws Exception {
    Path jar = Files.createFile(temporaryDirectory.resolve("example.jar"));
    CacheStore store = new CacheStore(configuration(Duration.ofDays(30), 3L));
    store.put(jar, BINARY_NAME, 21, ENGINE_VERSION, OPTIONS, "one");
    Path firstEntry = sourceEntries().get(0);
    Files.setLastModifiedTime(firstEntry, java.nio.file.attribute.FileTime.fromMillis(0L));

    store.put(jar, "example.Other", 21, ENGINE_VERSION, OPTIONS, "two");

    assertEquals(1, sourceEntries().size());
  }

  /** Confirms that independent store instances safely serialize writes to the same entry. */
  @Test
  void serializesConcurrentWritesAcrossStoreInstances() throws Exception {
    Path jar = Files.createFile(temporaryDirectory.resolve("example.jar"));
    JaroscopeConfiguration configuration = configuration(Duration.ofDays(30), 1024L);
    int writerCount = 8;
    ExecutorService writers = Executors.newFixedThreadPool(writerCount);
    try {
      List<Future<?>> writes = new ArrayList<>();
      for (int writerIndex = 0; writerIndex < writerCount; ++writerIndex) {
        int sourceNumber = writerIndex;
        writes.add(
            writers.submit(
                () -> {
                  try {
                    new CacheStore(configuration)
                        .put(
                            jar,
                            BINARY_NAME,
                            21,
                            ENGINE_VERSION,
                            OPTIONS,
                            "source-" + sourceNumber);
                  } catch (Exception exception) {
                    throw new RuntimeException(exception);
                  }
                }));
      }
      for (Future<?> write : writes) {
        write.get();
      }
    } finally {
      writers.shutdownNow();
    }

    Optional<String> result =
        new CacheStore(configuration).get(jar, BINARY_NAME, 21, ENGINE_VERSION, OPTIONS);
    assertTrue(result.orElseThrow().matches("source-[0-7]"));
    assertEquals(1, sourceEntries().size());
  }

  /** Returns cache source files currently present in the temporary cache directory. */
  private List<Path> sourceEntries() throws Exception {
    try (Stream<Path> paths = Files.list(temporaryDirectory)) {
      return paths.filter(path -> path.toString().endsWith(".source")).toList();
    }
  }

  /** Creates cache configuration for one test scenario. */
  private JaroscopeConfiguration configuration(Duration maxAge, long maxSize) {
    return new JaroscopeConfiguration(21, temporaryDirectory, maxAge, maxSize, List.of());
  }
}
