package pl.kaitou_dev.jaroscope.decompiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import pl.kaitou_dev.jaroscope.cache.CacheStore;
import pl.kaitou_dev.jaroscope.config.JaroscopeConfiguration;
import pl.kaitou_dev.jaroscope.core.JarIndex;

/** Schedules bounded, deduplicated, best-effort background cache warm-up jobs. */
@Slf4j
public final class BackgroundDecompilationCoordinator implements AutoCloseable {
  /** Maximum queued warm-up jobs in addition to running workers. */
  private static final int MAX_QUEUED_JOBS = 16;

  /** Seconds allowed for background jobs to finish during application shutdown. */
  private static final long SHUTDOWN_WAIT_SECONDS = 10L;

  private final JaroscopeConfiguration configuration;
  private final CachingDecompiler decompiler;
  private final ThreadPoolExecutor executor;
  private final ConcurrentHashMap<JobKey, CompletableFuture<Void>> activeJobs =
      new ConcurrentHashMap<>();
  private volatile boolean closed;

  /** Creates the configured bounded background worker pool. */
  public BackgroundDecompilationCoordinator(JaroscopeConfiguration configuration) {
    this(configuration, new VineflowerDecompiler());
  }

  /** Creates a worker pool using an injected decompiler implementation. */
  public BackgroundDecompilationCoordinator(
      JaroscopeConfiguration configuration, Decompiler engine) {
    this.configuration = configuration;
    decompiler = new CachingDecompiler(engine, new CacheStore(configuration));
    executor =
        new ThreadPoolExecutor(
            configuration.maxConcurrentBackgroundJars(),
            configuration.maxConcurrentBackgroundJars(),
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(MAX_QUEUED_JOBS),
            new BackgroundThreadFactory(),
            new ThreadPoolExecutor.AbortPolicy());
  }

  /**
   * Queues one whole-JAR cache warm-up unless disabled or already active.
   *
   * @param jarPath an already validated JAR path
   * @param targetRelease the Java release whose classes should be warmed
   * @return true if a new job was accepted, false if disabled, duplicate, closed, or saturated
   */
  public boolean schedule(Path jarPath, int targetRelease) {
    if (!configuration.backgroundDecompilationEnabled() || closed) {
      return false;
    }
    try {
      JobKey key = jobKey(jarPath, targetRelease);
      CompletableFuture<Void> active = new CompletableFuture<>();
      if (activeJobs.putIfAbsent(key, active) != null) {
        return false;
      }
      try {
        executor.execute(() -> runJob(key, active, jarPath, targetRelease));
        return true;
      } catch (java.util.concurrent.RejectedExecutionException exception) {
        activeJobs.remove(key, active);
        log.debug("Background warm-up queue is full jar={}", jarPath);
        return false;
      }
    } catch (IOException exception) {
      log.warn(
          "Could not schedule background warm-up jar={} reason={}",
          jarPath,
          exception.getMessage());
      return false;
    }
  }

  /** Stops accepting work and gives running decompilation jobs a bounded shutdown interval. */
  @Override
  public void close() {
    closed = true;
    executor.shutdown();
    try {
      if (!executor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException exception) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  private void runJob(JobKey key, CompletableFuture<Void> active, Path jarPath, int targetRelease) {
    try {
      List<String> classes =
          JarIndex.open(jarPath, targetRelease).classes().keySet().stream()
              .limit(configuration.maxBackgroundClasses())
              .toList();
      DecompilationBatchResult result =
          decompiler.decompileClasses(jarPath, classes, targetRelease);
      log.info(
          "Background warm-up completed jar={} classes={} hits={} decompiled={} failed={}",
          jarPath,
          classes.size(),
          result.cacheHits(),
          result.decompiled(),
          result.failedClasses().size());
    } catch (IOException | RuntimeException exception) {
      log.warn("Background warm-up failed jar={} reason={}", jarPath, exception.getMessage());
    } finally {
      active.complete(null);
      activeJobs.remove(key, active);
    }
  }

  private JobKey jobKey(Path jarPath, int targetRelease) throws IOException {
    Path realPath = jarPath.toRealPath();
    FileTime modified = Files.getLastModifiedTime(realPath);
    return new JobKey(realPath, targetRelease, Files.size(realPath), modified.toMillis());
  }

  private record JobKey(Path realPath, int targetRelease, long size, long modifiedMillis) {}

  private static final class BackgroundThreadFactory implements ThreadFactory {
    private static final String THREAD_PREFIX = "jaroscope-background-decompile-";
    private static final AtomicInteger NEXT_THREAD_ID = new AtomicInteger();

    /** Creates a named worker thread that participates in application shutdown. */
    @Override
    public Thread newThread(Runnable task) {
      Thread thread = new Thread(task, THREAD_PREFIX + NEXT_THREAD_ID.incrementAndGet());
      thread.setDaemon(false);
      return thread;
    }
  }
}
