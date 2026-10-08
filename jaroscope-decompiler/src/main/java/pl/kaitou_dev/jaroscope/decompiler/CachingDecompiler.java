package pl.kaitou_dev.jaroscope.decompiler;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import pl.kaitou_dev.jaroscope.cache.CacheStore;

/** Decorates a decompiler with reusable content-addressed source caching. */
@Slf4j
public final class CachingDecompiler implements Decompiler {
  /** Current Vineflower engine version used in cache identity. */
  public static final String ENGINE_VERSION = "1.12.0";

  /** Cache identity for the current decompiler options. */
  public static final String OPTIONS_FINGERPRINT = "default";

  private final Decompiler delegate;
  private final CacheStore cache;

  /** Creates a caching decorator around a decompiler and cache store. */
  public CachingDecompiler(Decompiler delegate, CacheStore cache) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.cache = Objects.requireNonNull(cache, "cache");
  }

  /** Returns cached source or delegates to Vineflower on a miss. */
  @Override
  public DecompiledClass decompile(Path jarPath, String binaryName, int targetRelease)
      throws IOException {
    Optional<String> cached =
        cache.get(jarPath, binaryName, targetRelease, ENGINE_VERSION, OPTIONS_FINGERPRINT);
    if (cached.isPresent()) {
      log.debug("Decompiler cache hit class={} targetRelease={}", binaryName, targetRelease);
      return new DecompiledClass(binaryName, targetRelease, cached.orElseThrow());
    }
    DecompiledClass result = delegate.decompile(jarPath, binaryName, targetRelease);
    cache.put(
        jarPath, binaryName, targetRelease, ENGINE_VERSION, OPTIONS_FINGERPRINT, result.source());
    return result;
  }

  /**
   * Returns cached results and decompiles all misses through one batch-capable engine invocation.
   *
   * @param jarPath the source JAR
   * @param binaryNames selected classes
   * @param targetRelease the Java release
   * @return batch cache-hit, emitted, and missing-source summary
   * @throws IOException if the JAR or cache cannot be read or written
   */
  public DecompilationBatchResult decompileClasses(
      Path jarPath, List<String> binaryNames, int targetRelease) throws IOException {
    int cacheHits = 0;
    List<String> misses = new ArrayList<>();
    Set<String> emitted = ConcurrentHashMap.newKeySet();
    try (CacheStore.CacheSession session =
        cache.openSession(jarPath, targetRelease, ENGINE_VERSION, OPTIONS_FINGERPRINT)) {
      for (String binaryName : binaryNames) {
        if (session.get(binaryName).isPresent()) {
          ++cacheHits;
        } else {
          misses.add(binaryName);
        }
      }
      if (!misses.isEmpty()) {
        delegate.decompileClasses(
            jarPath,
            misses,
            targetRelease,
            result -> {
              try {
                session.put(result.binaryName(), result.source());
                emitted.add(result.binaryName());
              } catch (IOException exception) {
                throw new UncheckedIOException(exception);
              }
            });
      }
    }
    Set<String> successful = new HashSet<>(emitted);
    successful.retainAll(misses);
    List<String> failed = misses.stream().filter(name -> !successful.contains(name)).toList();
    return new DecompilationBatchResult(cacheHits, successful.size(), failed);
  }
}
