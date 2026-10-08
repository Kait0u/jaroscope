package pl.kaitou_dev.jaroscope.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.jar.JarOutputStream;
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
    Path jar = createJar("example.jar");
    CacheStore store = new CacheStore(configuration(Duration.ofDays(30), 1024L));

    store.put(jar, BINARY_NAME, 21, ENGINE_VERSION, OPTIONS, "class Greeter {}");

    assertEquals(
        Optional.of("class Greeter {}"), store.get(jar, BINARY_NAME, 21, ENGINE_VERSION, OPTIONS));
    assertEquals(1, sourceEntries().size());
    Path renamedJar = Files.copy(jar, temporaryDirectory.resolve("renamed.jar"));
    assertEquals(
        Optional.of("class Greeter {}"),
        store.get(renamedJar, BINARY_NAME, 21, ENGINE_VERSION, OPTIONS));
    assertEquals(1, sourceEntries().size());
    Path sourcePath = sourceEntries().get(0);
    Path relativeSourcePath = temporaryDirectory.relativize(sourcePath);
    assertEquals("java-21", relativeSourcePath.getName(1).toString());
    assertEquals("vineflower-1.12.0", relativeSourcePath.getName(2).toString());
    assertEquals("sources", relativeSourcePath.getName(4).toString());
    assertEquals("example", relativeSourcePath.getName(5).toString());
    assertEquals("Greeter.java", relativeSourcePath.getFileName().toString());
    Path artifactDirectory = temporaryDirectory.resolve(relativeSourcePath.getName(0));
    assertTrue(relativeSourcePath.getName(0).toString().matches("[0-9a-f]{64}"));
    String metadata = Files.readString(artifactDirectory.resolve("artifact.properties"));
    assertTrue(metadata.contains("example.jar"));
    assertTrue(metadata.contains("renamed.jar"));
  }

  /** Confirms that changing the class or release produces a different cache entry. */
  @Test
  void separatesRequestIdentity() throws Exception {
    Path jar = createJar("example.jar");
    CacheStore store = new CacheStore(configuration(Duration.ofDays(30), 1024L));

    store.put(jar, BINARY_NAME, 21, ENGINE_VERSION, OPTIONS, "source");

    assertTrue(store.get(jar, BINARY_NAME, 17, ENGINE_VERSION, OPTIONS).isEmpty());
  }

  /** Confirms that entries older than the configured age are removed. */
  @Test
  void removesExpiredEntries() throws Exception {
    Path jar = createJar("example.jar");
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
    Path jar = createJar("example.jar");
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
    Path jar = createJar("example.jar");
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

  /** Confirms that legacy flat entries are promoted into the package-oriented artifact tree. */
  @Test
  void promotesLegacyFlatCacheEntry() throws Exception {
    Path jar = createJar("legacy.jar");
    String jarHash =
        HexFormat.of()
            .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));
    String requestHash =
        digest(String.join("\u0000", jarHash, BINARY_NAME, "21", ENGINE_VERSION, OPTIONS));
    Path legacyEntry = temporaryDirectory.resolve(requestHash + ".source");
    Files.writeString(legacyEntry, "legacy source");

    Optional<String> result =
        new CacheStore(configuration(Duration.ofDays(30), 1024L))
            .get(jar, BINARY_NAME, 21, ENGINE_VERSION, OPTIONS);

    assertEquals(Optional.of("legacy source"), result);
    assertFalse(Files.exists(legacyEntry));
    assertEquals("legacy source", Files.readString(sourceEntries().get(0)));
  }

  /** Returns cache source files currently present in the temporary cache directory. */
  private List<Path> sourceEntries() throws Exception {
    try (Stream<Path> paths = Files.walk(temporaryDirectory)) {
      return paths
          .filter(Files::isRegularFile)
          .filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".source"))
          .toList();
    }
  }

  /** Creates an empty but structurally valid JAR for cache identity tests. */
  private Path createJar(String fileName) throws Exception {
    Path jar = temporaryDirectory.resolve(fileName);
    try (JarOutputStream ignored = new JarOutputStream(Files.newOutputStream(jar))) {
      return jar;
    }
  }

  /** Computes the SHA-256 digest used by the previous flat cache layout. */
  private String digest(String value) throws Exception {
    return HexFormat.of()
        .formatHex(
            MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  }

  /** Creates cache configuration for one test scenario. */
  private JaroscopeConfiguration configuration(Duration maxAge, long maxSize) {
    return new JaroscopeConfiguration(21, temporaryDirectory, maxAge, maxSize, List.of());
  }
}
