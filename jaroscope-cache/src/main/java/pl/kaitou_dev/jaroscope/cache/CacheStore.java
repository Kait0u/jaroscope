package pl.kaitou_dev.jaroscope.cache;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.module.FindException;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
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
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import pl.kaitou_dev.jaroscope.config.JaroscopeConfiguration;
import pl.kaitou_dev.jaroscope.core.JarIndexException;

/**
 * Content-addressed storage for decompiled source with bounded cleanup.
 *
 * <p>Every cache read, write, and cleanup operation uses one JVM and OS file lock. This prevents
 * concurrent writers from publishing the same cache file, including writers in separate JARoscope
 * processes. Decompilation itself occurs outside this lock.
 */
public final class CacheStore {
  /** Current decompiled source file suffix. */
  private static final String SOURCE_SUFFIX = ".java";

  /** Previous flat-cache suffix retained for transparent cache promotion. */
  private static final String LEGACY_ENTRY_SUFFIX = ".source";

  /** Per-artifact metadata filename. */
  private static final String ARTIFACT_METADATA_FILE = "artifact.properties";

  /** Alias property key prefix in artifact metadata. */
  private static final String ALIAS_PROPERTY_PREFIX = "alias.";

  /** Maven coordinate property key prefix in artifact metadata. */
  private static final String MAVEN_COORDINATE_PROPERTY_PREFIX = "maven.";

  /** Maven metadata directory in standard Java artifacts. */
  private static final String MAVEN_METADATA_PREFIX = "META-INF/maven/";

  /** Maven metadata properties filename. */
  private static final String MAVEN_PROPERTIES_SUFFIX = "/pom.properties";

  /** Maximum bytes read from one embedded Maven properties entry. */
  private static final long MAX_MAVEN_PROPERTIES_BYTES = 1024L * 1024L;

  /** Maximum embedded Maven properties entries read from one JAR. */
  private static final int MAX_MAVEN_METADATA_ENTRIES = 32;

  /** Maximum aggregate bytes read from embedded Maven metadata. */
  private static final long MAX_MAVEN_METADATA_BYTES = 4L * 1024L * 1024L;

  /** Prefixes and manifest attributes used to describe artifact identity claims. */
  private static final String IMPLEMENTATION_TITLE_ATTRIBUTE = "Implementation-Title";

  private static final String IMPLEMENTATION_VERSION_ATTRIBUTE = "Implementation-Version";
  private static final String AUTOMATIC_MODULE_NAME_ATTRIBUTE = "Automatic-Module-Name";
  private static final String BUNDLE_SYMBOLIC_NAME_ATTRIBUTE = "Bundle-SymbolicName";
  private static final String BUNDLE_VERSION_ATTRIBUTE = "Bundle-Version";
  private static final String GROUP_ID_PROPERTY = "groupId";
  private static final String ARTIFACT_ID_PROPERTY = "artifactId";
  private static final String VERSION_PROPERTY = "version";

  /** Release-specific source subtree name prefix. */
  private static final String RELEASE_DIRECTORY_PREFIX = "java-";

  /** Decompiler-specific source subtree name prefix. */
  private static final String ENGINE_DIRECTORY_PREFIX = "vineflower-";

  /** Options fingerprint subtree name prefix. */
  private static final String OPTIONS_DIRECTORY_PREFIX = "options-";

  /** Source subtree directory. */
  private static final String SOURCES_DIRECTORY = "sources";

  /** Marker prepended to encoded path components that are not safe readable names. */
  private static final String ENCODED_COMPONENT_PREFIX = "~";

  /** Maximum UTF-8 length retained for a readable filesystem path component. */
  private static final int MAX_READABLE_COMPONENT_BYTES = 100;

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
    try (CacheSession session =
        openSession(jarPath, targetRelease, engineVersion, optionsFingerprint)) {
      return session.get(binaryName);
    }
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
    try (CacheSession session =
        openSession(jarPath, targetRelease, engineVersion, optionsFingerprint)) {
      session.put(binaryName, source);
    }
  }

  /**
   * Opens a scoped cache accessor that hashes the JAR once for multiple class operations.
   *
   * @param jarPath the source JAR
   * @param targetRelease the selected Java release
   * @param engineVersion the decompiler engine version
   * @param optionsFingerprint the decompiler options identity
   * @return a session that must be closed after use
   * @throws IOException if the source JAR cannot be read
   */
  public CacheSession openSession(
      Path jarPath, int targetRelease, String engineVersion, String optionsFingerprint)
      throws IOException {
    if (Files.size(jarPath) > configuration.maxArchiveBytes()) {
      throw new JarIndexException("JAR exceeds the configured archive size limit");
    }
    String jarSha256 = sha256(jarPath);
    recordArtifactMetadata(jarPath, jarSha256);
    return new CacheSession(jarSha256, targetRelease, engineVersion, optionsFingerprint);
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
          pruneEmptyArtifacts();
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
          pruneEmptyArtifacts();
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
          for (Path artifactDirectory : artifactDirectories()) {
            long artifactBytes = treeSize(artifactDirectory);
            int artifactEntries = (int) sourceEntriesUnder(artifactDirectory).size();
            deleteTree(artifactDirectory);
            removedEntries += artifactEntries;
            removedBytes += artifactBytes;
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

  /** Records aliases and embedded artifact metadata beside the content-addressed source tree. */
  private void recordArtifactMetadata(Path jarPath, String jarSha256) throws IOException {
    withCacheLock(
        () -> {
          Path artifactDirectory = artifactDirectory(jarSha256);
          Files.createDirectories(artifactDirectory);
          Path metadataPath = artifactDirectory.resolve(ARTIFACT_METADATA_FILE);
          Properties metadata = new Properties();
          if (Files.isRegularFile(metadataPath)) {
            try (InputStream metadataInput = Files.newInputStream(metadataPath)) {
              metadata.load(metadataInput);
            }
          }
          boolean changed = false;
          changed |= setIfAbsent(metadata, "artifact.sha256", jarSha256);
          String originalFileName =
              jarPath.getFileName() == null ? jarPath.toString() : jarPath.getFileName().toString();
          changed |=
              setIfAbsent(
                  metadata, ALIAS_PROPERTY_PREFIX + digest(originalFileName), originalFileName);
          changed |=
              setIfAbsent(metadata, "artifact.size-bytes", Long.toString(Files.size(jarPath)));
          if (!Boolean.parseBoolean(metadata.getProperty("artifact.identity-scanned"))) {
            scanEmbeddedIdentity(jarPath, metadata);
            scanExplicitModuleIdentity(jarPath, metadata);
            metadata.setProperty("artifact.identity-scanned", Boolean.TRUE.toString());
            changed = true;
          }
          if (changed) {
            writeMetadata(metadataPath, metadata);
          }
          return null;
        });
  }

  /** Adds a non-null metadata claim only when it has not already been recorded. */
  private boolean setIfAbsent(Properties metadata, String key, String value) {
    if (value == null || metadata.containsKey(key)) {
      return false;
    }
    metadata.setProperty(key, value);
    return true;
  }

  /** Reads bounded manifest and Maven coordinate claims without loading archive classes. */
  private void scanEmbeddedIdentity(Path jarPath, Properties metadata) throws IOException {
    try (JarFile jar = new JarFile(jarPath.toFile(), false, JarFile.OPEN_READ)) {
      JarEntry manifestEntry = jar.getJarEntry("META-INF/MANIFEST.MF");
      Manifest manifest =
          manifestEntry != null && manifestEntry.getSize() <= MAX_MAVEN_PROPERTIES_BYTES
              ? jar.getManifest()
              : null;
      if (manifest != null) {
        Attributes attributes = manifest.getMainAttributes();
        setIfAbsent(
            metadata,
            "manifest.implementation-title",
            attributes.getValue(IMPLEMENTATION_TITLE_ATTRIBUTE));
        setIfAbsent(
            metadata,
            "manifest.implementation-version",
            attributes.getValue(IMPLEMENTATION_VERSION_ATTRIBUTE));
        setIfAbsent(
            metadata,
            "manifest.automatic-module-name",
            attributes.getValue(AUTOMATIC_MODULE_NAME_ATTRIBUTE));
        setIfAbsent(
            metadata,
            "manifest.bundle-symbolic-name",
            attributes.getValue(BUNDLE_SYMBOLIC_NAME_ATTRIBUTE));
        setIfAbsent(
            metadata, "manifest.bundle-version", attributes.getValue(BUNDLE_VERSION_ATTRIBUTE));
      }
      var entries = jar.entries();
      int entryCount = 0;
      int mavenMetadataEntryCount = 0;
      long mavenMetadataBytes = 0L;
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        ++entryCount;
        if (entryCount > configuration.maxArchiveEntries()) {
          throw new JarIndexException("JAR exceeds the configured entry-count limit");
        }
        String entryName = entry.getName();
        if (!entryName.startsWith(MAVEN_METADATA_PREFIX)
            || !entryName.endsWith(MAVEN_PROPERTIES_SUFFIX)
            || entry.getSize() > MAX_MAVEN_PROPERTIES_BYTES
            || mavenMetadataEntryCount >= MAX_MAVEN_METADATA_ENTRIES
            || mavenMetadataBytes >= MAX_MAVEN_METADATA_BYTES) {
          continue;
        }
        ++mavenMetadataEntryCount;
        Properties coordinates = new Properties();
        try (InputStream input = jar.getInputStream(entry)) {
          long remainingBudget = MAX_MAVEN_METADATA_BYTES - mavenMetadataBytes;
          int readLimit = (int) Math.min(MAX_MAVEN_PROPERTIES_BYTES, remainingBudget) + 1;
          byte[] propertiesBytes = input.readNBytes(readLimit);
          if (propertiesBytes.length > MAX_MAVEN_PROPERTIES_BYTES
              || propertiesBytes.length > remainingBudget) {
            continue;
          }
          mavenMetadataBytes += propertiesBytes.length;
          coordinates.load(new java.io.ByteArrayInputStream(propertiesBytes));
        }
        String groupId = coordinates.getProperty(GROUP_ID_PROPERTY);
        String artifactId = coordinates.getProperty(ARTIFACT_ID_PROPERTY);
        String version = coordinates.getProperty(VERSION_PROPERTY);
        if (groupId != null && artifactId != null && version != null) {
          String coordinate = groupId + ":" + artifactId + ":" + version;
          setIfAbsent(metadata, MAVEN_COORDINATE_PROPERTY_PREFIX + digest(coordinate), coordinate);
        }
      }
    }
  }

  /** Records an explicit module descriptor without accepting filename-derived automatic names. */
  /** Records an explicit module descriptor without trusting automatic filename-derived names. */
  private void scanExplicitModuleIdentity(Path jarPath, Properties metadata) {
    try {
      for (ModuleReference reference : ModuleFinder.of(jarPath).findAll()) {
        java.lang.module.ModuleDescriptor descriptor = reference.descriptor();
        if (descriptor.isAutomatic()) {
          continue;
        }
        setIfAbsent(metadata, "module.name", descriptor.name());
        descriptor
            .rawVersion()
            .ifPresent(version -> setIfAbsent(metadata, "module.version", version));
      }
    } catch (FindException exception) {
      metadata.setProperty(
          "module.metadata-warning", "Explicit module descriptor could not be read");
    }
  }

  /** Atomically writes artifact metadata using a temporary sibling file. */
  private void writeMetadata(Path metadataPath, Properties metadata) throws IOException {
    Path temporary = Files.createTempFile(metadataPath.getParent(), "artifact-", TEMP_SUFFIX);
    try {
      try (OutputStream output = Files.newOutputStream(temporary)) {
        metadata.store(output, null);
      }
      try {
        Files.move(
            temporary,
            metadataPath,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException exception) {
        Files.move(temporary, metadataPath, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  /** Writes source to its package path under the hash-addressed artifact directory. */
  private void writeEntry(CacheKey key, String source) throws IOException {
    Path entry = entryPath(key);
    Files.createDirectories(entry.getParent());
    Path temporary = Files.createTempFile(entry.getParent(), keyHash(key), TEMP_SUFFIX);
    try {
      Files.writeString(temporary, source, StandardCharsets.UTF_8);
      try {
        Files.move(
            temporary, entry, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException exception) {
        Files.move(temporary, entry, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  /** Scoped cache access with one content hash and one cleanup pass after bulk stores. */
  public final class CacheSession implements AutoCloseable {
    private final String jarSha256;
    private final int targetRelease;
    private final String engineVersion;
    private final String optionsFingerprint;
    private boolean dirty;
    private boolean closed;

    private CacheSession(
        String jarSha256, int targetRelease, String engineVersion, String optionsFingerprint) {
      this.jarSha256 = jarSha256;
      this.targetRelease = targetRelease;
      this.engineVersion = engineVersion;
      this.optionsFingerprint = optionsFingerprint;
    }

    /** Looks up one class result and refreshes its access time when present. */
    public synchronized Optional<String> get(String binaryName) throws IOException {
      ensureOpen();
      CacheKey key = cacheKey(binaryName);
      Path entry = entryPath(key);
      return withCacheLock(
          () -> {
            Path sourcePath = entry;
            if (!Files.isRegularFile(sourcePath)) {
              Path legacyPath = legacyEntryPath(key);
              if (!Files.isRegularFile(legacyPath)) {
                return Optional.empty();
              }
              String legacySource = Files.readString(legacyPath);
              writeEntry(key, legacySource);
              Files.deleteIfExists(legacyPath);
              dirty = true;
            }
            String source = Files.readString(sourcePath);
            Files.setLastModifiedTime(sourcePath, FileTime.from(Instant.now()));
            return Optional.of(source);
          });
    }

    /** Stores one class result under the exclusive cache filesystem lock. */
    public synchronized void put(String binaryName, String source) throws IOException {
      ensureOpen();
      CacheKey key = cacheKey(binaryName);
      withCacheLock(
          () -> {
            writeEntry(key, source);
            return null;
          });
      dirty = true;
    }

    /** Runs cache cleanup once after a session that stored one or more class results. */
    @Override
    public synchronized void close() throws IOException {
      if (closed) {
        return;
      }
      closed = true;
      if (dirty) {
        cleanup();
      }
    }

    private CacheKey cacheKey(String binaryName) {
      return new CacheKey(jarSha256, binaryName, targetRelease, engineVersion, optionsFingerprint);
    }

    private void ensureOpen() {
      if (closed) {
        throw new IllegalStateException("Cache session is closed");
      }
    }
  }

  @FunctionalInterface
  /** An I/O operation executed while the cache lock is held. */
  private interface IoOperation<T> {
    T run() throws IOException;
  }

  /** Resolves a source key to its release, engine, options, and package path. */
  private Path entryPath(CacheKey key) {
    String[] classNameParts = key.binaryName().split("\\.", -1);
    if (classNameParts.length == 0) {
      throw new CacheException("Binary class name cannot be empty", new IllegalArgumentException());
    }
    Path sourceDirectory =
        artifactDirectory(key.jarSha256())
            .resolve(RELEASE_DIRECTORY_PREFIX + key.targetRelease())
            .resolve(ENGINE_DIRECTORY_PREFIX + pathComponent(key.engineVersion()))
            .resolve(OPTIONS_DIRECTORY_PREFIX + digest(key.optionsFingerprint()))
            .resolve(SOURCES_DIRECTORY);
    for (int index = 0; index < classNameParts.length - 1; ++index) {
      sourceDirectory = sourceDirectory.resolve(pathComponent(classNameParts[index]));
    }
    String sourceFileName =
        pathComponent(classNameParts[classNameParts.length - 1]) + SOURCE_SUFFIX;
    Path entry = sourceDirectory.resolve(sourceFileName).normalize();
    if (!entry.startsWith(artifactDirectory(key.jarSha256()))) {
      throw new CacheException(
          "Source path escaped its artifact directory", new IllegalArgumentException());
    }
    return entry;
  }

  /** Resolves the previous flat-layout path for an entry being lazily migrated. */
  private Path legacyEntryPath(CacheKey key) {
    return configuration.cacheDirectory().resolve(keyHash(key) + LEGACY_ENTRY_SUFFIX);
  }

  /** Resolves the top-level content-hash directory for an artifact. */
  private Path artifactDirectory(String jarSha256) {
    return configuration.cacheDirectory().resolve(jarSha256);
  }

  /** Keeps common Java identifiers readable and encodes unsafe filesystem components. */
  private String pathComponent(String value) {
    byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
    if (isPortableReadableComponent(value, valueBytes.length)) {
      return value;
    }
    return ENCODED_COMPONENT_PREFIX + HexFormat.of().formatHex(valueBytes);
  }

  /** Checks characters, length, traversal names, and reserved device names. */
  private boolean isPortableReadableComponent(String value, int utf8Length) {
    if (value.isEmpty()
        || value.equals(".")
        || value.equals("..")
        || value.endsWith(".")
        || utf8Length > MAX_READABLE_COMPONENT_BYTES
        || isWindowsReservedName(value)) {
      return false;
    }
    for (int index = 0; index < value.length(); ++index) {
      char character = value.charAt(index);
      boolean allowed =
          character >= 'a' && character <= 'z'
              || character >= 'A' && character <= 'Z'
              || character >= '0' && character <= '9'
              || character == '$'
              || character == '_'
              || character == '-'
              || character == '.';
      if (!allowed) {
        return false;
      }
    }
    return true;
  }

  /** Checks reserved DOS device names that remain reserved with extensions. */
  private boolean isWindowsReservedName(String value) {
    String normalized = value.toUpperCase(java.util.Locale.ROOT);
    if (normalized.equals("CON")
        || normalized.equals("PRN")
        || normalized.equals("AUX")
        || normalized.equals("NUL")) {
      return true;
    }
    return normalized.matches("(?:COM|LPT)[1-9]");
  }

  /** Hashes all source identity dimensions for legacy-cache migration. */
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

  /** Computes a streaming SHA-256 digest of the exact JAR bytes. */
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

  /** Computes a UTF-8 SHA-256 digest for metadata keys and encoded path components. */
  private String digest(String value) {
    return HexFormat.of().formatHex(messageDigest().digest(value.getBytes(StandardCharsets.UTF_8)));
  }

  /** Creates the standard JDK SHA-256 digest implementation. */
  private MessageDigest messageDigest() {
    try {
      return MessageDigest.getInstance(DIGEST_ALGORITHM);
    } catch (NoSuchAlgorithmException exception) {
      throw new CacheException("Required digest algorithm is unavailable", exception);
    }
  }

  /** Lists new package-tree sources and any flat legacy sources awaiting migration. */
  private List<Path> entries() throws IOException {
    List<Path> result = new ArrayList<>();
    Path cacheDirectory = configuration.cacheDirectory();
    if (!Files.isDirectory(cacheDirectory)) {
      return result;
    }
    try (Stream<Path> paths = Files.walk(cacheDirectory)) {
      for (Path entry : paths.filter(Files::isRegularFile).toList()) {
        Path relative = cacheDirectory.relativize(entry);
        boolean legacySource =
            relative.getNameCount() == 1
                && entry.getFileName().toString().endsWith(LEGACY_ENTRY_SUFFIX);
        boolean catalogSource =
            relative.getNameCount() >= 3
                && isArtifactHash(relative.getName(0).toString())
                && entry.getFileName().toString().endsWith(SOURCE_SUFFIX);
        if (legacySource || catalogSource) {
          result.add(entry);
        }
      }
    }
    return result;
  }

  /** Lists only direct cache children whose names are valid lowercase SHA-256 hashes. */
  private List<Path> artifactDirectories() throws IOException {
    if (!Files.isDirectory(configuration.cacheDirectory())) {
      return List.of();
    }
    try (Stream<Path> paths = Files.list(configuration.cacheDirectory())) {
      return paths
          .filter(path -> Files.isDirectory(path) && isArtifactHash(path.getFileName().toString()))
          .toList();
    }
  }

  /** Lists package-tree Java source files under one artifact. */
  private List<Path> sourceEntriesUnder(Path artifactDirectory) throws IOException {
    try (Stream<Path> paths = Files.walk(artifactDirectory)) {
      return paths
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(SOURCE_SUFFIX))
          .toList();
    }
  }

  /** Checks the 64-character lowercase hexadecimal artifact directory convention. */
  private boolean isArtifactHash(String value) {
    return value.length() == 64
        && value
            .chars()
            .allMatch(
                character ->
                    character >= '0' && character <= '9' || character >= 'a' && character <= 'f');
  }

  /** Removes artifact metadata directories after their final source entry is pruned. */
  private void pruneEmptyArtifacts() throws IOException {
    for (Path artifactDirectory : artifactDirectories()) {
      if (sourceEntriesUnder(artifactDirectory).isEmpty()) {
        deleteTree(artifactDirectory);
      }
    }
  }

  /** Sums regular file sizes inside one cache-owned artifact directory. */
  private long treeSize(Path root) throws IOException {
    try (Stream<Path> paths = Files.walk(root)) {
      return paths.filter(Files::isRegularFile).mapToLong(this::size).sum();
    }
  }

  /** Deletes a verified cache-owned artifact directory from leaf paths upward. */
  private void deleteTree(Path root) throws IOException {
    try (Stream<Path> paths = Files.walk(root)) {
      for (Path entry :
          paths.sorted(Comparator.comparingInt(Path::getNameCount).reversed()).toList()) {
        Files.deleteIfExists(entry);
      }
    }
  }

  /** Returns a file's size, treating a concurrent disappearance as zero. */
  private long size(Path path) {
    try {
      return Files.size(path);
    } catch (IOException exception) {
      return 0L;
    }
  }

  /** Returns a file's last-modified instant, using epoch when it disappeared. */
  private Instant lastModified(Path path) {
    try {
      return Files.getLastModifiedTime(path).toInstant();
    } catch (IOException exception) {
      return Instant.EPOCH;
    }
  }
}
