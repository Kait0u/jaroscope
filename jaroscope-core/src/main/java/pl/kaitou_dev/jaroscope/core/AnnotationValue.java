package pl.kaitou_dev.jaroscope.core;

/** A typed annotation element value that does not require loading referenced classes. */
public sealed interface AnnotationValue
    permits PrimitiveAnnotationValue,
        EnumAnnotationValue,
        ClassAnnotationValue,
        NestedAnnotationValue,
        ArrayAnnotationValue {}
