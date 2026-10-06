package pl.kaitou_dev.jaroscope.core;

/** A primitive or string annotation value represented in source-oriented text. */
public record PrimitiveAnnotationValue(String value) implements AnnotationValue {}
