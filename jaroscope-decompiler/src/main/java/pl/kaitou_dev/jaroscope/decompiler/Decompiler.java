package pl.kaitou_dev.jaroscope.decompiler;

import java.io.IOException;
import java.nio.file.Path;

/** Decompiles one selected class from a local JAR. */
public interface Decompiler {
  /**
   * Decompiles a class for the requested Java release.
   *
   * @param jarPath the JAR containing the class
   * @param binaryName the binary class name
   * @param targetRelease the Java release used for class selection
   * @return the decompiled source
   * @throws IOException if the archive cannot be read
   * @throws DecompilerException if source cannot be produced
   */
  DecompiledClass decompile(Path jarPath, String binaryName, int targetRelease) throws IOException;
}
