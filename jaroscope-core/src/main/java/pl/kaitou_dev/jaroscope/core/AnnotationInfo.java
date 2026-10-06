package pl.kaitou_dev.jaroscope.core;

import java.util.Map;

/** Declared annotation metadata extracted without resolving the annotation type. */
public record AnnotationInfo(
    String typeName, boolean runtimeVisible, Map<String, AnnotationValue> values) {
  /** Creates an immutable annotation metadata value. */
  public AnnotationInfo {
    values = Map.copyOf(values);
  }
}
