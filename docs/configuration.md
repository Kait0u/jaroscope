# Configuration and path policy

JARoscope loads configuration in three layers:

1. Bundled `application.yml` defaults.
2. `~/.jaroscope/application.yml`, when present.
3. An explicit configuration path, when supplied.

Later layers override matching values and preserve unrelated values. The
configuration library validates release, cache, and security settings before
returning a record used by the application.

The default maximum source response is 1 MiB and is configured with
`jaroscope.response.max-source-size`.

Background cache warming is enabled by default. Configure it with
`jaroscope.background-decompilation.enabled`, `max-concurrent-jars`, and
`max-classes`.

Invalid configuration raises `InvalidConfigurationException`. A rejected JAR
path raises `JarPathException`. Filesystem failures remain `IOException`.

```mermaid
sequenceDiagram
    participant App
    participant Loader
    participant Defaults as Bundled defaults
    participant Home as ~/.jaroscope/application.yml
    participant Override as Explicit file
    App->>Loader: load(userHome, optionalOverride)
    Loader->>Defaults: read
    Defaults-->>Loader: YAML tree
    opt Home file exists
        Loader->>Home: read
        Home-->>Loader: YAML tree
        Loader->>Loader: deep merge
    end
    opt Explicit file supplied
        Loader->>Override: read
        Override-->>Loader: YAML tree
        Loader->>Loader: deep merge
    end
    Loader->>Loader: validate and expand ~
    Loader-->>App: JaroscopeConfiguration
```

`JarPathPolicy` resolves a requested path to its real path before checking it.
This prevents a symlink from bypassing a banned root. It accepts only regular
files with a `.jar` suffix. Banned roots are also resolved through existing
symlinked ancestors, including when the configured root's final path does not
yet exist.

```mermaid
flowchart TD
    A[Requested path] --> B[Resolve real path]
    B --> C{Regular .jar file?}
    C -- No --> D[Reject]
    C -- Yes --> E{Under a banned root?}
    E -- Yes --> F[Reject]
    E -- No --> G[Return permitted real path]
```
