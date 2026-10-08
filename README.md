# JARoscope

JARoscope is a local stdio MCP server for exploring Java JARs from clients such
as Claude Code and OpenCode. Source requests trigger bounded background cache
warming by default. The server exposes `list_classes`,
`get_class_interface`, `get_class_source`, `cache_status`, `clean_cache`, and
`decompile_jar`.

## Modules

- `jaroscope-core`: archive indexing and bytecode-based class-interface extraction.
- `jaroscope-config`: typed YAML configuration and JAR path policy.
- `jaroscope-mcp`: owns the stdio transport and stderr logging configuration.
- `jaroscope-decompiler`: Vineflower adapter, cache decorator, and background warm-up coordinator.
- `buildSrc`: shared Java toolchain, JUnit and Google Java Style via Spotless.

The index selects the highest available class version no greater than the
requested Java release, provided the manifest declares `Multi-Release: true`.
JAR inspection and interface extraction do not load classes. Vineflower source
is cached under `~/.jaroscope/cache`, and class selection honors multi-release
JAR versions.
See [JAR indexing internals](docs/jar-index.md) for the current selection logic.
See [class-interface extraction](docs/class-interface.md) for bytecode metadata extraction.
See [decompiler internals](docs/decompiler.md) for source extraction.
See [cache internals](docs/cache.md) for cache identity and cleanup.
See [configuration and path policy](docs/configuration.md) for configuration layering and access checks.
See [logging](docs/logging.md) for the stderr format and stream boundary.
See [application flow](docs/application.md) for startup and transport behavior.
See [Claude Code and OpenCode setup](docs/clients.md) for client configuration.
See [`list_classes`](docs/tools/list-classes.md) for the class discovery tool contract.
See [`get_class_interface`](docs/tools/get-class-interface.md) for interface extraction.
See [`get_class_source`](docs/tools/get-class-source.md) for cached source extraction.
See [`cache_status`](docs/tools/cache-status.md) and [`clean_cache`](docs/tools/clean-cache.md) for cache administration.
See [`decompile_jar`](docs/tools/decompile-jar.md) for explicit bulk decompilation.

## Build and test

Run `gradle check spotlessCheck`. `gradle spotlessApply` formats Java sources
and Gradle Kotlin scripts. The core and MCP tests use prebundled multi-release
JARs; see `jaroscope-core/src/test/fixture-sources/README.md` to regenerate it.
Testament produces a cross-module test summary when tests run.

Build and run the stdio server with `gradle :jaroscope-mcp:installDist`, then
execute `jaroscope-mcp/build/install/jaroscope-mcp/bin/jaroscope-mcp`. Use
`--config /path/to/application.yml` to supply an explicit configuration file.

The project's license has not yet been selected.
