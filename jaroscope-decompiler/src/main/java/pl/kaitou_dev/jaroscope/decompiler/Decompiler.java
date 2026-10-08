package pl.kaitou_dev.jaroscope.decompiler;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Decompiles one selected class from a local JAR. */
@FunctionalInterface
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

  /**
   * Decompiles selected classes in one engine invocation and streams completed source values.
   *
   * @param jarPath the JAR containing the classes
   * @param binaryNames the binary names to emit
   * @param targetRelease the Java release used for multi-release selection
   * @param sourceConsumer receives each emitted class source
   * @return binary names for which source was emitted
   * @throws IOException if the archive cannot be read
   * @throws DecompilerException if the engine cannot process the batch
   */
  /**
   * Decompiles requested classes and forwards each source result.
   *
   * <p>The default implementation invokes {@link #decompile(Path, String, int)} for each class.
   * Batch-capable engines should override this method to process the input in one invocation.
   *
   * @param jarPath the JAR containing the classes
   * @param binaryNames the requested binary class names
   * @param targetRelease the Java release used for class selection
   * @param sourceConsumer receives each decompiled class
   * @return the class names for which source was produced
   * @throws IOException if the JAR cannot be read
   */
  default Set<String> decompileClasses(
      Path jarPath,
      List<String> binaryNames,
      int targetRelease,
      Consumer<DecompiledClass> sourceConsumer)
      throws IOException {
    Set<String> emitted = new HashSet<>();
    for (String binaryName : binaryNames) {
      DecompiledClass result = decompile(jarPath, binaryName, targetRelease);
      sourceConsumer.accept(result);
      emitted.add(binaryName);
    }
    return Set.copyOf(emitted);
  }
}
