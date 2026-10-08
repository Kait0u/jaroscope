package pl.kaitou_dev.jaroscope.core;

import java.util.List;

/** A public or protected declared or inherited method. */
public record MethodInterface(
    String name,
    String returnType,
    List<String> parameterTypes,
    MemberVisibility visibility,
    boolean isStatic,
    String genericSignature,
    List<AnnotationInfo> annotations) {
  /** Creates an immutable method description. */
  public MethodInterface {
    parameterTypes = List.copyOf(parameterTypes);
    annotations = List.copyOf(annotations);
  }
}
