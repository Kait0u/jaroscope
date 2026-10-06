package pl.kaitou_dev.jaroscope.core;

import java.util.List;

/** A public or protected method declared by a class. */
public record MethodInterface(
    String name,
    String returnType,
    List<String> parameterTypes,
    MemberVisibility visibility,
    String genericSignature,
    List<AnnotationInfo> annotations) {
  /** Creates an immutable method description. */
  public MethodInterface {
    parameterTypes = List.copyOf(parameterTypes);
    annotations = List.copyOf(annotations);
  }
}
