# Class interface extraction

`ClassInterfaceExtractor` reads the selected class entry from a JAR with ASM.
It returns project-owned records and never loads the class or its annotations.

The default result contains declared public and protected fields, constructors,
methods, generic signatures, and annotations. Synthetic and bridge members are
excluded. Inherited members are not resolved by this operation.

```mermaid
sequenceDiagram
    participant Caller
    participant Index as JarIndex
    participant Jar as JarFile
    participant ASM as ASM ClassReader
    Caller->>Index: select binaryName and targetRelease
    Index-->>Caller: selected entry name
    Caller->>Jar: open selected entry
    Jar-->>ASM: class bytes
    ASM->>ASM: visit declarations and annotations
    ASM-->>Caller: immutable ClassInterface records
    Caller->>Jar: close
```

Annotation values remain unresolved metadata:

```mermaid
classDiagram
    ClassInterface o-- AnnotationInfo
    AnnotationInfo o-- AnnotationValue
    AnnotationValue <|.. PrimitiveAnnotationValue
    AnnotationValue <|.. EnumAnnotationValue
    AnnotationValue <|.. ClassAnnotationValue
    AnnotationValue <|.. NestedAnnotationValue
    AnnotationValue <|.. ArrayAnnotationValue
```

Class literals are returned as type names. They are never resolved through a
class loader.
