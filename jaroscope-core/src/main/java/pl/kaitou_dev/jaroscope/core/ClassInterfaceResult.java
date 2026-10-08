package pl.kaitou_dev.jaroscope.core;

import java.util.List;

/** Class interface metadata and warnings produced during optional hierarchy resolution. */
public record ClassInterfaceResult(ClassInterface classInterface, List<String> warnings) {
  /** Creates a result with an immutable warning list. */
  public ClassInterfaceResult {
    warnings = List.copyOf(warnings);
  }
}
