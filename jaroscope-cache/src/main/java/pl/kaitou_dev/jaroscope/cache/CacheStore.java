package pl.kaitou_dev.jaroscope.cache;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import pl.kaitou_dev.jaroscope.config.JaroscopeConfiguration;

/**
 * Content-addressed storage for decompiled source with bounded cleanup.
 *
 * <p>Every cache read, write, and cleanup operation uses one JVM and OS file lock. This prevents
 * concurrent writers from publishing the same cache file, including writers in separate JARoscope
 * processes. Decompilation itself occurs outside this lock.
 */
public final class CacheStore {
  /** Cache entry suffix. */
  private static final String ENTRY_SUFFIX = ".source";

  /** Temporary entry suffix used before an atomic move. */
  private static final String TEMP_SUFFIX = ".tmp";

  /** Digest algorithm used for JAR and cache identities. */
  private static final String DIGEST_ALGORITHM = "SHA-256";

  /** Persistent inter-process lock filename within the cache directory. */
  private static final String CACHE_LOCK_FILE = ".jaroscope-cache.lock";

  /** JVM-local locks prevent overlapping file-lock requests from threads in one process. */
  private static final ConcurrentHashMap<Path, ReentrantLock> JVM_LOCKS = new ConcurrentHashMap<>();

  private final JaroscopeConfiguration configuration;

  /** Creates a cache store using the configured directory and cleanup limits. */
  public CacheStore(JaroscopeConfiguration configuration) {
    this.configuration = Objects.requireNonNull(configuration, "configuration");
  }

  /**
   * Looks up source for a JAR and request identity.
   *
   * @param jarPath the source JAR
   * @param binaryName the requested class
   * @param targetRelease the selected Java release
   * @param engineVersion the decompiler engine version
   * @param optionsFingerprint the decompiler options identity
   * @return cached source when present and readable
   * @throws IOException if the JAR or cache cannot be read
   */
  public Optional<String> get(
      Path jarPath,
      String binaryName,
      int targetRelease,
      String engineVersion,
      String optionsFingerprint)
      throws IOException {
    CacheKey key = key(jarPath, binaryName, targetRelease, engineVersion, optionsFingerprint);
    Path entry = entryPath(key);
    return withCacheLock(
        () -> {
          if (!Files.isRegularFile(entry)) {
            return Optional.empty();
          }
          String source = Files.readString(entry);
          Files.setLastModifiedTime(entry, FileTime.from(Instant.now()));
          return Optional.of(source);
        });
  }

  /**
   * Stores source under an atomic content-addressed entry and applies cleanup.
   *
   * @param jarPath the source JAR
   * @param binaryName the requested class
   * @param targetRelease the selected Java release
   * @param engineVersion the decompiler engine version
   * @param optionsFingerprint the decompiler options identity
   * @param source the decompiled source
   * @throws IOException if the cache cannot be written
   */
  public void put(
      Path jarPath,
      String binaryName,
      int targetRelease,
      String engineVersion,
      String optionsFingerprint,
      String source)
      throws IOException {
    CacheKey key = key(jarPath, binaryName, targetRelease, engineVersion, optionsFingerprint);
    Path entry = entryPath(key);
    withCacheLock(
        () -> {
          Path temporary =
              Files.createTempFile(configuration.cacheDirectory(), keyHash(key), TEMP_SUFFIX);
          try {
            Files.writeString(temporary, source, StandardCharsets.UTF_8);
            try {
              Files.move(
                  temporary,
                  entry,
                  StandardCopyOption.ATOMIC_MOVE,
                  StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
              Files.move(temporary, entry, StandardCopyOption.REPLACE_EXISTING);
            }
          } finally {
            Files.deleteIfExists(temporary);
          }
          return null;
        });
    cleanup();
  }

  /**
   * Returns current cache usage and cleanup eligibility.
   *
   * @return an immutable cache status snapshot
   * @throws IOException if the cache directory cannot be read
   */
  public CacheStatus status() throws IOException {
    return withCacheLock(
        () -> {
          List<Path> cacheEntries = entries();
          Instant expiry = Instant.now().minus(configuration.cacheMaxAge());
          List<Instant> modifiedTimes =
              cacheEntries.stream().map(this::lastModified).sorted().toList();
          long totalBytes = cacheEntries.stream().mapToLong(this::size).sum();
          int expiredCount =
              (int)
                  cacheEntries.stream()
                      .filter(entry -> lastModified(entry).isBefore(expiry))
                      .count();
          return new CacheStatus(
              configuration.cacheDirectory(),
              cacheEntries.size(),
              totalBytes,
              modifiedTimes.isEmpty() ? Optional.empty() : Optional.of(modifiedTimes.get(0)),
              modifiedTimes.isEmpty()
                  ? Optional.empty()
                  : Optional.of(modifiedTimes.get(modifiedTimes.size() - 1)),
              expiredCount,
              Math.max(0L, totalBytes - configuration.cacheMaxSizeBytes()),
              configuration.cacheMaxSizeBytes(),
              configuration.cacheMaxAge());
        });
  }

  /** Removes expired entries and then oldest entries until the configured size limit is met. */
  public CacheCleanupResult cleanup() throws IOException {
    return withCacheLock(
        () -> {
          if (!Files.isDirectory(configuration.cacheDirectory())) {
            return new CacheCleanupResult(0, 0L);
          }
          Instant expiry = Instant.now().minus(configuration.cacheMaxAge());
          List<Path> entries = entries();
          int removedEntries = 0;
          long removedBytes = 0L;
          for (Path entry : entries) {
            if (Files.getLastModifiedTime(entry).toInstant().isBefore(expiry)) {
              long entrySize = size(entry);
              if (Files.deleteIfExists(entry)) {
                ++removedEntries;
                removedBytes += entrySize;
              }
            }
          }
          List<Path> remaining = entries();
          long totalSize = remaining.stream().mapToLong(this::size).sum();
          if (totalSize <= configuration.cacheMaxSizeBytes()) {
            return new CacheCleanupResult(removedEntries, removedBytes);
          }
          remaining.sort(Comparator.comparing(this::lastModified));
          for (Path entry : remaining) {
            if (totalSize <= configuration.cacheMaxSizeBytes()) {
              break;
            }
            long entrySize = size(entry);
            totalSize -= entrySize;
            if (Files.deleteIfExists(entry)) {
              ++removedEntries;
              removedBytes += entrySize;
            }
          }
          return new CacheCleanupResult(removedEntries, removedBytes);
        });
  }

  /** Removes every owned source entry without touching other files. */
  public CacheCleanupResult clear() throws IOException {
    return withCacheLock(
        () -> {
          int removedEntries = 0;
          long removedBytes = 0L;
          for (Path entry : entries()) {
            long entrySize = size(entry);
            if (Files.deleteIfExists(entry)) {
              ++removedEntries;
              removedBytes += entrySize;
            }
          }
          return new CacheCleanupResult(removedEntries, removedBytes);
        });
  }

  /** Runs a cache filesystem operation under JVM-local and cross-process exclusive locks. */
  private <T> T withCacheLock(IoOperation<T> operation) throws IOException {
    Path cacheDirectory = configuration.cacheDirectory().toAbsolutePath().normalize();
    Files.createDirectories(cacheDirectory);
    Path lockPath = cacheDirectory.resolve(CACHE_LOCK_FILE);
    ReentrantLock jvmLock = JVM_LOCKS.computeIfAbsent(lockPath, ignored -> new ReentrantLock());
    jvmLock.lock();
    try (FileChannel channel =
            FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock ignored = channel.lock()) {
      return operation.run();
    } finally {
      jvmLock.unlock();
    }
  }

  @FunctionalInterface
  /** An I/O operation executed while the cache lock is held. */
  private interface IoOperation<T> {
    T run() throws IOException;
  }

  private CacheKey key(
      Path jarPath,
      String binaryName,
      int targetRelease,
      String engineVersion,
      String optionsFingerprint)
      throws IOException {
    return new CacheKey(
        sha256(jarPath), binaryName, targetRelease, engineVersion, optionsFingerprint);
  }

  private Path entryPath(CacheKey key) {
    return configuration.cacheDirectory().resolve(keyHash(key) + ENTRY_SUFFIX);
  }

  private String keyHash(CacheKey key) {
    return digest(
        String.join(
            "\u0000",
            key.jarSha256(),
            key.binaryName(),
            Integer.toString(key.targetRelease()),
            key.engineVersion(),
            key.optionsFingerprint()));
  }

  private String sha256(Path path) throws IOException {
    try (InputStream input = Files.newInputStream(path)) {
      MessageDigest digest = messageDigest();
      byte[] buffer = new byte[8192];
      int read;
      while ((read = input.read(buffer)) >= 0) {
        digest.update(buffer, 0, read);
      }
      return HexFormat.of().formatHex(digest.digest());
    }
  }

  private String digest(String value) {
    return HexFormat.of().formatHex(messageDigest().digest(value.getBytes(StandardCharsets.UTF_8)));
  }

  private MessageDigest messageDigest() {
    try {
      return MessageDigest.getInstance(DIGEST_ALGORITHM);
    } catch (NoSuchAlgorithmException exception) {
      throw new CacheException("Required digest algorithm is unavailable", exception);
    }
  }

  private List<Path> entries() throws IOException {
    List<Path> result = new ArrayList<>();
    try (DirectoryStream<Path> stream =
        Files.newDirectoryStream(configuration.cacheDirectory(), "*" + ENTRY_SUFFIX)) {
      for (Path entry : stream) {
        if (Files.isRegularFile(entry)) {
          result.add(entry);
        }
      }
    }
    return result;
  }

  private long size(Path path) {
    try {
      return Files.size(path);
    } catch (IOException exception) {
      return 0L;
    }
  }

  private Instant lastModified(Path path) {
    try {
      return Files.getLastModifiedTime(path).toInstant();
    } catch (IOException exception) {
      return Instant.EPOCH;
    }
  }
}
