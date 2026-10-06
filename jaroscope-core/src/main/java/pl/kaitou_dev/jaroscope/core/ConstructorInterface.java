package pl.kaitou_dev.jaroscope.core;

import java.util.List;

/** A public or protected constructor declared by a class. */
public record ConstructorInterface(
    List<String> parameterTypes, String genericSignature, List<AnnotationInfo> annotations) {
  /** Creates an immutable constructor description. */
  public ConstructorInterface {
    parameterTypes = List.copyOf(parameterTypes);
    annotations = List.copyOf(annotations);
  }
}
