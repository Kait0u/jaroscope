package pl.kaitou_dev.jaroscope.decompiler;

import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.Manifest;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.java.decompiler.api.Decompiler.Builder;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;
import pl.kaitou_dev.jaroscope.core.JarIndex;

/** Vineflower-backed implementation of the project decompiler boundary. */
@Slf4j
public final class VineflowerDecompiler implements Decompiler {
  /**
   * Decompiles one selected class and filters Vineflower output to that class.
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
    JarIndex.open(jarPath, targetRelease)
        .entryFor(binaryName)
        .orElseThrow(() -> new DecompilerException("Class not found: " + binaryName));
    SourceSaver saver = new SourceSaver(binaryName);
    try {
      new Builder().inputs(jarPath.toFile()).output(saver).build().decompile();
    } catch (IllegalArgumentException exception) {
      throw new DecompilerException("Vineflower rejected class: " + binaryName, exception);
    }
    String source = saver.source();
    if (source == null || source.isBlank()) {
      throw new DecompilerException("Vineflower produced no source: " + binaryName);
    }
    log.info("Decompiled class={} targetRelease={}", binaryName, targetRelease);
    return new DecompiledClass(binaryName, targetRelease, source);
  }

  private static final class SourceSaver implements IResultSaver {
    private final String binaryName;
    private String source;

    /** Creates a saver that retains only the requested class source. */
    private SourceSaver(String binaryName) {
      this.binaryName = binaryName;
    }

    /** Returns the captured source, or null when Vineflower emitted no match. */
    private String source() {
      return source;
    }

    /** Ignores folder creation because source is retained in memory. */
    @Override
    public void saveFolder(String path) {}

    /** Ignores copied resources because only Java source is requested. */
    @Override
    public void copyFile(String source, String path, String entryName) {}

    /** Captures a class source callback when it matches the requested class. */
    @Override
    public void saveClassFile(
        String path, String qualifiedName, String entryName, String content, int[] mapping) {
      capture(qualifiedName, content);
    }

    /** Ignores archive creation because source is retained in memory. */
    @Override
    public void createArchive(String path, String archiveName, Manifest manifest) {}

    /** Ignores directory entries because source is retained in memory. */
    @Override
    public void saveDirEntry(String path, String archiveName, String entryName) {}

    /** Ignores copied archive entries because only Java source is requested. */
    @Override
    public void copyEntry(String source, String path, String archiveName, String entry) {}

    /** Captures an archive source callback when it matches the requested class. */
    @Override
    public void saveClassEntry(
        String path, String archiveName, String qualifiedName, String entryName, String content) {
      capture(qualifiedName, content);
    }

    /** Ignores archive closure because source is retained in memory. */
    @Override
    public void closeArchive(String path, String archiveName) {}

    /** Retains source only when Vineflower identifies the requested binary class. */
    private void capture(String qualifiedName, String content) {
      if (binaryName.equals(qualifiedName) || binaryName.equals(qualifiedName.replace('/', '.'))) {
        source = content;
      }
    }
  }
}
