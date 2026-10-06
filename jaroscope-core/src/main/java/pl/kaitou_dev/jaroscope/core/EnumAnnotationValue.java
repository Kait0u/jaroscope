package pl.kaitou_dev.jaroscope.core;

/** An annotation enum value represented by its type and constant names. */
public record EnumAnnotationValue(String typeName, String constantName)
    implements AnnotationValue {}
