package pl.kaitou_dev.jaroscope.core;

import java.util.List;

/** The declared public and protected interface of one class-file entry. */
public record ClassInterface(
    String binaryName,
    ClassKind kind,
    String superClassName,
    List<String> interfaceNames,
    List<AnnotationInfo> annotations,
    List<FieldInterface> fields,
    List<ConstructorInterface> constructors,
    List<MethodInterface> methods) {
  /** Creates an immutable class interface description. */
  public ClassInterface {
    interfaceNames = List.copyOf(interfaceNames);
    annotations = List.copyOf(annotations);
    fields = List.copyOf(fields);
    constructors = List.copyOf(constructors);
    methods = List.copyOf(methods);
  }
}
