package pl.kaitou_dev.jaroscope.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.jar.JarFile;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** Extracts declared class-interface metadata from bytecode without loading classes. */
public final class ClassInterfaceExtractor {
  private static final int ASM_API = Opcodes.ASM9;
  private static final int SKIP_CLASS_CONTENT =
      ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES;
  private static final String OBJECT_TYPE = "java.lang.Object";
  private static final String CONSTRUCTOR_NAME = "<init>";
  private static final String INTERNAL_NAME_SEPARATOR = "/";
  private static final String BINARY_NAME_SEPARATOR = ".";
  private final ClassInheritanceResolver inheritanceResolver = new ClassInheritanceResolver(this);

  /**
   * Extracts public and protected members and declared annotations for one selected class.
   *
   * @param jarPath the JAR to inspect
   * @param binaryName the requested binary class name
   * @param targetRelease the Java release used for multi-release selection
   * @return the declared class interface
   * @throws IOException if the archive or selected class cannot be read
   * @throws ClassInterfaceException if the class is absent or malformed
   */
  public ClassInterface extract(Path jarPath, String binaryName, int targetRelease)
      throws IOException {
    Objects.requireNonNull(jarPath, "jarPath");
    Objects.requireNonNull(binaryName, "binaryName");
    return extract(jarPath, binaryName, JarIndex.open(jarPath, targetRelease));
  }

  /** Extracts a selected class using a prebuilt index shared by hierarchy traversal. */
  ClassInterface extract(Path jarPath, String binaryName, JarIndex index) throws IOException {
    String entryName =
        index
            .entryFor(binaryName)
            .orElseThrow(() -> new ClassInterfaceException("Class not found: " + binaryName));
    try (JarFile jar = new JarFile(jarPath.toFile(), false, JarFile.OPEN_READ)) {
      try (InputStream classBytes = jar.getInputStream(jar.getJarEntry(entryName))) {
        InterfaceVisitor visitor = new InterfaceVisitor(binaryName);
        try {
          new ClassReader(classBytes).accept(visitor, SKIP_CLASS_CONTENT);
        } catch (IllegalArgumentException exception) {
          throw new ClassInterfaceException("Malformed class: " + binaryName, exception);
        }
        return visitor.result();
      }
    }
  }

  /**
   * Extracts a class interface and optionally resolves members and inherited annotations from the
   * same JAR.
   *
   * @param jarPath the JAR to inspect
   * @param binaryName the requested binary class name
   * @param targetRelease the Java release used for multi-release selection
   * @param includeInheritedMembers whether to resolve parents present in this JAR
   * @return the interface and any warnings for unresolved parent types
   * @throws IOException if the archive cannot be read
   * @throws ClassInterfaceException if the requested class is missing or malformed
   */
  public ClassInterfaceResult extractWithInheritance(
      Path jarPath, String binaryName, int targetRelease, boolean includeInheritedMembers)
      throws IOException {
    ClassInterface declared = extract(jarPath, binaryName, targetRelease);
    if (!includeInheritedMembers) {
      return new ClassInterfaceResult(declared, List.of());
    }
    return inheritanceResolver.resolve(jarPath, declared, targetRelease);
  }

  private static MemberVisibility visibility(int access) {
    return (access & Opcodes.ACC_PUBLIC) != 0
        ? MemberVisibility.PUBLIC
        : MemberVisibility.PROTECTED;
  }

  private static boolean isVisible(int access) {
    return (access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0;
  }

  private static boolean isSynthetic(int access) {
    return (access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0;
  }

  private static String typeName(Type type) {
    return type.getClassName();
  }

  private static String binaryName(String internalName) {
    return internalName.replace(INTERNAL_NAME_SEPARATOR, BINARY_NAME_SEPARATOR);
  }

  private static ClassKind classKind(int access) {
    if ((access & Opcodes.ACC_ANNOTATION) != 0) {
      return ClassKind.ANNOTATION;
    }
    if ((access & Opcodes.ACC_ENUM) != 0) {
      return ClassKind.ENUM;
    }
    if ((access & Opcodes.ACC_RECORD) != 0) {
      return ClassKind.RECORD;
    }
    if ((access & Opcodes.ACC_INTERFACE) != 0) {
      return ClassKind.INTERFACE;
    }
    return ClassKind.CLASS;
  }

  private static final class InterfaceVisitor extends ClassVisitor {
    private final String requestedBinaryName;
    private final List<AnnotationInfo> annotations = new ArrayList<>();
    private final List<FieldState> fields = new ArrayList<>();
    private final List<ConstructorState> constructors = new ArrayList<>();
    private final List<MethodState> methods = new ArrayList<>();
    private String binaryName;
    private ClassKind kind;
    private String superClassName;
    private List<String> interfaceNames;

    private InterfaceVisitor(String requestedBinaryName) {
      super(ASM_API);
      this.requestedBinaryName = requestedBinaryName;
    }

    @Override
    public void visit(
        int version,
        int access,
        String name,
        String signature,
        String superName,
        String[] interfaces) {
      binaryName = binaryName(name);
      if (!binaryName.equals(requestedBinaryName)) {
        throw new ClassInterfaceException(
            "Class name mismatch: expected " + requestedBinaryName + " but found " + binaryName);
      }
      kind = classKind(access);
      superClassName = superName == null ? null : binaryName(superName);
      if (superClassName == null && kind != ClassKind.INTERFACE) {
        superClassName = OBJECT_TYPE;
      }
      interfaceNames = Arrays.stream(interfaces).map(ClassInterfaceExtractor::binaryName).toList();
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
      return new AnnotationCollector(descriptor, visible, annotations);
    }

    @Override
    public org.objectweb.asm.FieldVisitor visitField(
        int access, String name, String descriptor, String signature, Object value) {
      if (isVisible(access) && !isSynthetic(access)) {
        FieldState field =
            new FieldState(name, typeName(Type.getType(descriptor)), visibility(access), signature);
        fields.add(field);
        return new FieldAnnotationVisitor(field.annotations);
      }
      return null;
    }

    @Override
    public org.objectweb.asm.MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      if (isVisible(access) && !isSynthetic(access)) {
        Type methodType = Type.getMethodType(descriptor);
        List<String> parameters =
            List.of(methodType.getArgumentTypes()).stream()
                .map(ClassInterfaceExtractor::typeName)
                .toList();
        if (CONSTRUCTOR_NAME.equals(name)) {
          ConstructorState constructor = new ConstructorState(parameters, signature);
          constructors.add(constructor);
          return new MethodAnnotationVisitor(constructor.annotations);
        } else {
          MethodState method =
              new MethodState(
                  name,
                  typeName(methodType.getReturnType()),
                  parameters,
                  visibility(access),
                  (access & Opcodes.ACC_STATIC) != 0,
                  signature);
          methods.add(method);
          return new MethodAnnotationVisitor(method.annotations);
        }
      }
      return null;
    }

    private ClassInterface result() {
      return new ClassInterface(
          binaryName,
          kind,
          superClassName,
          interfaceNames,
          annotations,
          fields.stream().map(FieldState::toInterface).toList(),
          constructors.stream().map(ConstructorState::toInterface).toList(),
          methods.stream().map(MethodState::toInterface).toList());
    }
  }

  private static final class FieldState {
    private final String name;
    private final String typeName;
    private final MemberVisibility visibility;
    private final String genericSignature;
    private final List<AnnotationInfo> annotations = new ArrayList<>();

    private FieldState(
        String name, String typeName, MemberVisibility visibility, String genericSignature) {
      this.name = name;
      this.typeName = typeName;
      this.visibility = visibility;
      this.genericSignature = genericSignature;
    }

    private FieldInterface toInterface() {
      return new FieldInterface(name, typeName, visibility, genericSignature, annotations);
    }
  }

  private static final class ConstructorState {
    private final List<String> parameterTypes;
    private final String genericSignature;
    private final List<AnnotationInfo> annotations = new ArrayList<>();

    private ConstructorState(List<String> parameterTypes, String genericSignature) {
      this.parameterTypes = parameterTypes;
      this.genericSignature = genericSignature;
    }

    private ConstructorInterface toInterface() {
      return new ConstructorInterface(parameterTypes, genericSignature, annotations);
    }
  }

  private static final class MethodState {
    private final String name;
    private final String returnType;
    private final List<String> parameterTypes;
    private final MemberVisibility visibility;
    private final boolean isStatic;
    private final String genericSignature;
    private final List<AnnotationInfo> annotations = new ArrayList<>();

    private MethodState(
        String name,
        String returnType,
        List<String> parameterTypes,
        MemberVisibility visibility,
        boolean isStatic,
        String genericSignature) {
      this.name = name;
      this.returnType = returnType;
      this.parameterTypes = parameterTypes;
      this.visibility = visibility;
      this.isStatic = isStatic;
      this.genericSignature = genericSignature;
    }

    private MethodInterface toInterface() {
      return new MethodInterface(
          name, returnType, parameterTypes, visibility, isStatic, genericSignature, annotations);
    }
  }

  private static final class AnnotationCollector extends AnnotationVisitor {
    private final String descriptor;
    private final boolean visible;
    private final List<AnnotationInfo> target;
    private final Map<String, AnnotationValue> values = new TreeMap<>();

    private AnnotationCollector(String descriptor, boolean visible, List<AnnotationInfo> target) {
      super(ASM_API);
      this.descriptor = descriptor;
      this.visible = visible;
      this.target = target;
    }

    @Override
    public void visit(String name, Object value) {
      values.put(name, annotationValue(value));
    }

    @Override
    public void visitEnum(String name, String descriptor, String value) {
      values.put(
          name,
          new EnumAnnotationValue(binaryName(Type.getType(descriptor).getClassName()), value));
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
      return new NestedAnnotationCollector(name, descriptor, values);
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      return new ArrayAnnotationCollector(name, values);
    }

    @Override
    public void visitEnd() {
      target.add(
          new AnnotationInfo(binaryName(Type.getType(descriptor).getClassName()), visible, values));
    }

    private static AnnotationValue annotationValue(Object value) {
      if (value instanceof Type type) {
        return new ClassAnnotationValue(typeName(type));
      }
      return new PrimitiveAnnotationValue(String.valueOf(value));
    }
  }

  private static final class NestedAnnotationCollector extends AnnotationVisitor {
    private final String name;
    private final String descriptor;
    private final Map<String, AnnotationValue> parentValues;
    private final Map<String, AnnotationValue> values = new TreeMap<>();

    private NestedAnnotationCollector(
        String name, String descriptor, Map<String, AnnotationValue> parentValues) {
      super(ASM_API);
      this.name = name;
      this.descriptor = descriptor;
      this.parentValues = parentValues;
    }

    @Override
    public void visit(String name, Object value) {
      values.put(name, AnnotationCollector.annotationValue(value));
    }

    @Override
    public void visitEnd() {
      parentValues.put(
          name,
          new NestedAnnotationValue(
              new AnnotationInfo(
                  binaryName(Type.getType(descriptor).getClassName()), true, values)));
    }
  }

  private static final class ArrayAnnotationCollector extends AnnotationVisitor {
    private final String name;
    private final Map<String, AnnotationValue> parentValues;
    private final List<AnnotationValue> values = new ArrayList<>();

    private ArrayAnnotationCollector(String name, Map<String, AnnotationValue> parentValues) {
      super(ASM_API);
      this.name = name;
      this.parentValues = parentValues;
    }

    @Override
    public void visit(String name, Object value) {
      values.add(AnnotationCollector.annotationValue(value));
    }

    @Override
    public void visitEnd() {
      parentValues.put(name, new ArrayAnnotationValue(values));
    }
  }

  private static final class FieldAnnotationVisitor extends org.objectweb.asm.FieldVisitor {
    private final List<AnnotationInfo> annotations;

    private FieldAnnotationVisitor(List<AnnotationInfo> annotations) {
      super(ASM_API);
      this.annotations = annotations;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
      return new AnnotationCollector(descriptor, visible, annotations);
    }
  }

  private static final class MethodAnnotationVisitor extends org.objectweb.asm.MethodVisitor {
    private final List<AnnotationInfo> annotations;

    private MethodAnnotationVisitor(List<AnnotationInfo> annotations) {
      super(ASM_API);
      this.annotations = annotations;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
      return new AnnotationCollector(descriptor, visible, annotations);
    }
  }
}
