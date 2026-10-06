package pl.kaitou_dev.jaroscope.core;

/** A class-literal annotation value represented without loading the class. */
public record ClassAnnotationValue(String typeName) implements AnnotationValue {}
