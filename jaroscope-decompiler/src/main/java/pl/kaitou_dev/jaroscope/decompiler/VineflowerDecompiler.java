package pl.kaitou_dev.jaroscope.decompiler;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.java.decompiler.api.Decompiler.Builder;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;
import pl.kaitou_dev.jaroscope.config.ConfigurationConstants;
import pl.kaitou_dev.jaroscope.core.ArchiveLimits;
import pl.kaitou_dev.jaroscope.core.JarIndex;

/** Vineflower-backed implementation of the project decompiler boundary. */
@Slf4j
public final class VineflowerDecompiler implements Decompiler {
  /** The class-file suffix used for normalized release-specific view JARs. */
  private static final String CLASS_FILE_SUFFIX = ".class";

  /** Path separator used in JAR class entries. */
  private static final String INTERNAL_NAME_SEPARATOR = "/";

  /** Path separator in binary names. */
  private static final String BINARY_NAME_SEPARATOR = ".";

  /** Prefix used when creating temporary JAR views. */
  private static final String TEMP_JAR_PREFIX = "jaroscope-vineflower-";

  /** Suffix used when creating temporary JAR views. */
  private static final String TEMP_JAR_SUFFIX = ".jar";

  private final ArchiveLimits archiveLimits;
  private final int threadsPerRun;
  private final Semaphore runPermits;

  /** Creates a Vineflower adapter with default archive limits and a local bounded run gate. */
  public VineflowerDecompiler() {
    this(
        ArchiveLimits.defaults(),
        ConfigurationConstants.DEFAULT_VINEFLOWER_THREADS_PER_RUN,
        new Semaphore(ConfigurationConstants.DEFAULT_MAX_CONCURRENT_VINEFLOWER_RUNS, true));
  }

  /**
   * Creates a Vineflower adapter with shared resource limits and run permits.
   *
   * @param archiveLimits configured compressed, entry, and expanded class-byte limits
   * @param threadsPerRun Vineflower worker threads for one invocation
   * @param runPermits process-wide semaphore shared by foreground and background invocations
   */
  public VineflowerDecompiler(
      ArchiveLimits archiveLimits, int threadsPerRun, Semaphore runPermits) {
    this.archiveLimits = Objects.requireNonNull(archiveLimits, "archiveLimits");
    if (threadsPerRun < 1) {
      throw new DecompilerException("threadsPerRun must be positive");
    }
    this.threadsPerRun = threadsPerRun;
    this.runPermits = Objects.requireNonNull(runPermits, "runPermits");
  }

  /**
   * Decompiles one selected class using the same release-aware batch path as bulk operations.
   *
   * @param jarPath the JAR containing the class
   * @param binaryName the binary class name
   * @param targetRelease the Java release used for class selection
   * @return the decompiled source
   * @throws IOException if the archive cannot be read
   * @throws DecompilerException if Vineflower produces no source
   */
  @Override
  public DecompiledClass decompile(Path jarPath, String binaryName, int targetRelease)
      throws IOException {
    AtomicReference<DecompiledClass> result = new AtomicReference<>();
    Set<String> emitted =
        decompileClasses(jarPath, List.of(binaryName), targetRelease, result::set);
    if (!emitted.contains(binaryName)) {
      throw new DecompilerException("Vineflower produced no source: " + binaryName);
    }
    return result.get();
  }

  /**
   * Decompiles selected classes in one Vineflower invocation and streams emitted sources.
   *
   * <p>A release-resolved JAR is created for input classes and a second release-resolved JAR is
   * supplied as library context. This prevents Vineflower's own runtime release from selecting a
   * different multi-release implementation than the requested target release.
   *
   * @param jarPath the input JAR
   * @param binaryNames the classes whose source should be emitted
   * @param targetRelease the Java release used for multi-release selection
   * @param sourceConsumer receives each emitted source
   * @return binary class names for which source was emitted
   * @throws IOException if archive files cannot be read or temporary views cannot be built
   * @throws DecompilerException if requested classes are missing or Vineflower rejects the input
   */
  @Override
  public Set<String> decompileClasses(
      Path jarPath,
      List<String> binaryNames,
      int targetRelease,
      Consumer<DecompiledClass> sourceConsumer)
      throws IOException {
    JarIndex index = JarIndex.open(jarPath, targetRelease, archiveLimits);
    Map<String, String> selectedEntries = index.classes();
    for (String binaryName : binaryNames) {
      if (!selectedEntries.containsKey(binaryName)) {
        throw new DecompilerException("Class not found: " + binaryName);
      }
    }
    if (binaryNames.isEmpty()) {
      return Set.of();
    }

    try {
      runPermits.acquire();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new DecompilerException("Interrupted while waiting for a Vineflower worker", exception);
    }
    Path classContextJar = null;
    try {
      classContextJar = Files.createTempFile(TEMP_JAR_PREFIX, TEMP_JAR_SUFFIX);
      writeReleaseView(jarPath, selectedEntries, selectedEntries.keySet(), classContextJar);
      Set<String> requestedNames = Set.copyOf(binaryNames);
      Set<String> emittedNames = ConcurrentHashMap.newKeySet();
      SourceSaver saver =
          new SourceSaver(
              requestedNames,
              targetRelease,
              source -> {
                emittedNames.add(source.binaryName());
                sourceConsumer.accept(source);
              });
      try {
        String[] allowedPrefixes =
            binaryNames.stream().map(this::toInternalClassName).toArray(String[]::new);
        new Builder()
            .inputs(classContextJar.toFile())
            .allowedPrefixes(allowedPrefixes)
            .option(IFernflowerPreferences.THREADS, Integer.toString(threadsPerRun))
            .output(saver)
            .build()
            .decompile();
      } catch (IllegalArgumentException exception) {
        throw new DecompilerException("Vineflower rejected the requested classes", exception);
      } catch (UncheckedIOException exception) {
        throw exception.getCause();
      }
      log.info(
          "Vineflower batch completed requested={} emitted={} targetRelease={}",
          binaryNames.size(),
          emittedNames.size(),
          targetRelease);
      return Set.copyOf(emittedNames);
    } finally {
      if (classContextJar != null) {
        Files.deleteIfExists(classContextJar);
      }
      runPermits.release();
    }
  }

  /** Writes a target-release view while enforcing per-entry and aggregate expanded-byte caps. */
  private void writeReleaseView(
      Path jarPath, Map<String, String> selectedEntries, Iterable<String> binaryNames, Path output)
      throws IOException {
    try (JarFile input = new JarFile(jarPath.toFile(), false, JarFile.OPEN_READ);
        ZipOutputStream outputJar = new ZipOutputStream(Files.newOutputStream(output))) {
      Set<String> writtenEntries = new HashSet<>();
      long totalClassBytes = 0L;
      for (String binaryName : binaryNames) {
        String selectedEntryName = selectedEntries.get(binaryName);
        if (selectedEntryName == null) {
          continue;
        }
        String normalizedEntryName = toClassEntryName(binaryName);
        if (!writtenEntries.add(normalizedEntryName)) {
          continue;
        }
        JarEntry selectedEntry = input.getJarEntry(selectedEntryName);
        if (selectedEntry == null) {
          throw new IOException("Selected class entry disappeared: " + selectedEntryName);
        }
        if (selectedEntry.getSize() > archiveLimits.maxClassFileBytes()) {
          throw new DecompilerException(
              "Class entry exceeds the configured class-size limit: " + binaryName);
        }
        outputJar.putNextEntry(new ZipEntry(normalizedEntryName));
        try (InputStream classBytes = input.getInputStream(selectedEntry)) {
          byte[] buffer = new byte[8192];
          long entryBytes = 0L;
          int bytesRead;
          while ((bytesRead = classBytes.read(buffer)) >= 0) {
            entryBytes += bytesRead;
            totalClassBytes += bytesRead;
            if (entryBytes > archiveLimits.maxClassFileBytes()) {
              throw new DecompilerException(
                  "Class entry exceeds the configured class-size limit: " + binaryName);
            }
            if (totalClassBytes > archiveLimits.maxExpandedClassBytes()) {
              throw new DecompilerException(
                  "Selected class data exceeds the configured staging limit");
            }
            outputJar.write(buffer, 0, bytesRead);
          }
        }
        outputJar.closeEntry();
      }
    }
  }

  /** Converts a binary class name to Vineflower's internal slash-separated class name. */
  private String toInternalClassName(String binaryName) {
    return binaryName.replace(BINARY_NAME_SEPARATOR, INTERNAL_NAME_SEPARATOR);
  }

  private String toClassEntryName(String binaryName) {
    return toInternalClassName(binaryName) + CLASS_FILE_SUFFIX;
  }

  private static final class SourceSaver implements IResultSaver {
    private final Set<String> requestedNames;
    private final int targetRelease;
    private final Consumer<DecompiledClass> sourceConsumer;

    /** Creates a thread-safe result saver for one requested class set. */
    private SourceSaver(
        Set<String> requestedNames, int targetRelease, Consumer<DecompiledClass> sourceConsumer) {
      this.requestedNames = requestedNames;
      this.targetRelease = targetRelease;
      this.sourceConsumer = sourceConsumer;
    }

    /** Ignores folder creation because all results are forwarded to the consumer. */
    @Override
    public void saveFolder(String path) {}

    /** Ignores copied resources because only Java source is requested. */
    @Override
    public void copyFile(String source, String path, String entryName) {}

    /** Forwards an emitted source file if it belongs to the requested class set. */
    @Override
    public void saveClassFile(
        String path, String qualifiedName, String entryName, String content, int[] mapping) {
      capture(qualifiedName, content);
    }

    /** Ignores archive creation because results are forwarded in memory. */
    @Override
    public void createArchive(String path, String archiveName, Manifest manifest) {}

    /** Ignores directory entries because results are forwarded in memory. */
    @Override
    public void saveDirEntry(String path, String archiveName, String entryName) {}

    /** Ignores copied archive entries because only Java source is requested. */
    @Override
    public void copyEntry(String source, String path, String archiveName, String entry) {}

    /** Forwards an emitted archive source if it belongs to the requested class set. */
    @Override
    public void saveClassEntry(
        String path, String archiveName, String qualifiedName, String entryName, String content) {
      capture(qualifiedName, content);
    }

    /** Ignores archive closure because results are forwarded in memory. */
    @Override
    public void closeArchive(String path, String archiveName) {}

    /** Normalizes Vineflower's class name and forwards matching source. */
    private void capture(String qualifiedName, String content) {
      String binaryName = qualifiedName.replace(INTERNAL_NAME_SEPARATOR, BINARY_NAME_SEPARATOR);
      if (requestedNames.contains(binaryName)) {
        sourceConsumer.accept(new DecompiledClass(binaryName, targetRelease, content));
      }
    }
  }
}
