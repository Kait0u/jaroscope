package pl.kaitou_dev.jaroscope.decompiler;

import java.util.List;

/** Summary of one batch decompilation, including classes with no emitted source. */
public record DecompilationBatchResult(int cacheHits, int decompiled, List<String> failedClasses) {
  /** Creates an immutable batch result. */
  public DecompilationBatchResult {
    failedClasses = List.copyOf(failedClasses);
  }
}
