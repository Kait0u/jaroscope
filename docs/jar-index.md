# JAR class index

`JarIndex.open(path, targetRelease)` maps binary class names to archive entries.
It reads JAR metadata, does not load classes, and closes the archive before
returning an immutable, name-sorted map.

```mermaid
flowchart TD
    A[Archive entry] --> B{Directory?}
    B -- Yes --> Z[Skip]
    B -- No --> C{Versioned path?}
    C -- No --> F[Use base entry, version 0]
    C -- Yes --> D{Multi-Release manifest and version 9..targetRelease?}
    D -- No --> Z
    D -- Yes --> E[Strip META-INF/versions/N/]
    E --> G{Class file?}
    F --> G
    G -- No --> Z
    G -- Yes --> H{At least as new as selected entry?}
    H -- Yes --> I[Select entry for binary class name]
    H -- No --> Z
```

```mermaid
sequenceDiagram
    participant Caller
    participant Index as JarIndex
    participant Archive as JarFile
    Caller->>Index: open(path, targetRelease)
    Index->>Archive: open(path)
    Index->>Archive: isMultiRelease(), entries()
    loop Each entry
        Archive-->>Index: name and directory flag
        Index->>Index: select highest eligible class version
    end
    Index->>Archive: close()
    Index-->>Caller: immutable class-name to entry map
```

In the bundled fixture, `example.Greeter` maps to `example/Greeter.class`
at release 11 and `META-INF/versions/17/example/Greeter.class` at release 21.
Entries under `META-INF/versions/` are ignored unless the JAR declares
`Multi-Release: true`.
