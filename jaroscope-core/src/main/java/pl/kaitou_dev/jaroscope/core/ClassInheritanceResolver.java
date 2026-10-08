package pl.kaitou_dev.jaroscope.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves inherited members from classes and interfaces contained in one JAR. */
final class ClassInheritanceResolver {
  /** The root Java class whose inherited implementation is not searched in an input JAR. */
  private static final String OBJECT_BINARY_NAME = "java.lang.Object";

  /** The standard marker annotation that allows class annotations to be inherited. */
  private static final String INHERITED_ANNOTATION_NAME = "java.lang.annotation.Inherited";

  private final ClassInterfaceExtractor extractor;

  /** Creates a resolver that extracts parent metadata through the supplied bytecode extractor. */
  ClassInheritanceResolver(ClassInterfaceExtractor extractor) {
    this.extractor = extractor;
  }

  /** Resolves available ancestors while retaining declared members as the highest priority. */
  ClassInterfaceResult resolve(
      Path jarPath, ClassInterface declared, int targetRelease, ArchiveLimits limits)
      throws IOException {
    JarIndex index = JarIndex.open(jarPath, targetRelease, limits);
    Map<String, FieldInterface> fields = new LinkedHashMap<>();
    Map<MethodKey, MethodInterface> methods = new LinkedHashMap<>();
    Map<String, AnnotationInfo> annotations = new LinkedHashMap<>();
    Set<String> visited = new LinkedHashSet<>();
    Set<String> warnings = new LinkedHashSet<>();
    addDeclared(declared, fields, methods, annotations);
    visited.add(declared.binaryName());

    if (declared.superClassName() != null) {
      collect(
          jarPath,
          index,
          declared.superClassName(),
          true,
          limits.maxClassFileBytes(),
          visited,
          warnings,
          fields,
          methods,
          annotations);
    }
    for (String interfaceName : declared.interfaceNames()) {
      collect(
          jarPath,
          index,
          interfaceName,
          false,
          limits.maxClassFileBytes(),
          visited,
          warnings,
          fields,
          methods,
          annotations);
    }

    ClassInterface resolved =
        new ClassInterface(
            declared.binaryName(),
            declared.kind(),
            declared.superClassName(),
            declared.interfaceNames(),
            List.copyOf(annotations.values()),
            List.copyOf(fields.values()),
            declared.constructors(),
            List.copyOf(methods.values()));
    return new ClassInterfaceResult(resolved, List.copyOf(warnings));
  }

  /** Resolves one parent, adds its visible members, then visits its ancestors. */
  private void collect(
      Path jarPath,
      JarIndex index,
      String parentName,
      boolean superclassChain,
      long maxClassFileBytes,
      Set<String> visited,
      Set<String> warnings,
      Map<String, FieldInterface> fields,
      Map<MethodKey, MethodInterface> methods,
      Map<String, AnnotationInfo> annotations)
      throws IOException {
    if (OBJECT_BINARY_NAME.equals(parentName) || !visited.add(parentName)) {
      return;
    }
    ClassInterface parent;
    try {
      parent = extractor.extract(jarPath, parentName, index, maxClassFileBytes);
    } catch (ClassInterfaceException exception) {
      warnings.add("Could not resolve parent type " + parentName + " from the inspected JAR.");
      return;
    }

    addInheritedMembers(parent, fields, methods);
    if (superclassChain && parent.kind() != ClassKind.INTERFACE) {
      addInheritedAnnotations(jarPath, index, parent, maxClassFileBytes, annotations, warnings);
    }

    if (parent.superClassName() != null) {
      collect(
          jarPath,
          index,
          parent.superClassName(),
          superclassChain && parent.kind() != ClassKind.INTERFACE,
          maxClassFileBytes,
          visited,
          warnings,
          fields,
          methods,
          annotations);
    }
    for (String interfaceName : parent.interfaceNames()) {
      collect(
          jarPath,
          index,
          interfaceName,
          false,
          maxClassFileBytes,
          visited,
          warnings,
          fields,
          methods,
          annotations);
    }
  }

  /** Adds root declarations first so inherited declarations cannot replace them. */
  private void addDeclared(
      ClassInterface classInterface,
      Map<String, FieldInterface> fields,
      Map<MethodKey, MethodInterface> methods,
      Map<String, AnnotationInfo> annotations) {
    for (FieldInterface field : classInterface.fields()) {
      fields.putIfAbsent(field.name(), field);
    }
    for (MethodInterface method : classInterface.methods()) {
      methods.putIfAbsent(MethodKey.of(method), method);
    }
    for (AnnotationInfo annotation : classInterface.annotations()) {
      annotations.putIfAbsent(annotation.typeName(), annotation);
    }
  }

  /** Adds inheritable members and excludes static methods declared by interfaces. */
  private void addInheritedMembers(
      ClassInterface parent,
      Map<String, FieldInterface> fields,
      Map<MethodKey, MethodInterface> methods) {
    for (FieldInterface field : parent.fields()) {
      fields.putIfAbsent(field.name(), field);
    }
    for (MethodInterface method : parent.methods()) {
      if (parent.kind() != ClassKind.INTERFACE || !method.isStatic()) {
        methods.putIfAbsent(MethodKey.of(method), method);
      }
    }
  }

  /** Adds only superclass annotations whose annotation types declare {@code @Inherited}. */
  private void addInheritedAnnotations(
      Path jarPath,
      JarIndex index,
      ClassInterface parent,
      long maxClassFileBytes,
      Map<String, AnnotationInfo> annotations,
      Set<String> warnings) {
    for (AnnotationInfo annotation : parent.annotations()) {
      if (annotations.containsKey(annotation.typeName())) {
        continue;
      }
      try {
        ClassInterface annotationType =
            extractor.extract(jarPath, annotation.typeName(), index, maxClassFileBytes);
        boolean inheritable =
            annotationType.annotations().stream()
                .anyMatch(meta -> INHERITED_ANNOTATION_NAME.equals(meta.typeName()));
        if (inheritable) {
          annotations.put(annotation.typeName(), annotation);
        }
      } catch (ClassInterfaceException | IOException exception) {
        warnings.add(
            "Could not inspect annotation type " + annotation.typeName() + " for inheritance.");
      }
    }
  }

  private record MethodKey(String name, List<String> parameterTypes) {
    /** Creates an override key from a method name and its erased parameter types. */
    private static MethodKey of(MethodInterface method) {
      return new MethodKey(method.name(), method.parameterTypes());
    }
  }
}
