package pl.kaitou_dev.jaroscope.core;

import java.util.List;

/** A public or protected field declared by a class. */
public record FieldInterface(
    String name,
    String typeName,
    MemberVisibility visibility,
    String genericSignature,
    List<AnnotationInfo> annotations) {
  /** Creates an immutable field description. */
  public FieldInterface {
    annotations = List.copyOf(annotations);
  }
}
