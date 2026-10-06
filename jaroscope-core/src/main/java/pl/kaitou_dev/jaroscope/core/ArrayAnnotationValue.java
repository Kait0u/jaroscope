package pl.kaitou_dev.jaroscope.core;

import java.util.List;

/** An annotation array value with recursively typed elements. */
public record ArrayAnnotationValue(List<AnnotationValue> values) implements AnnotationValue {
  /** Creates an immutable annotation array value. */
  public ArrayAnnotationValue {
    values = List.copyOf(values);
  }
}
