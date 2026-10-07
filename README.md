# JARoscope

JARoscope is a local stdio MCP server for exploring Java JARs from clients such
as Claude Code and OpenCode. The server currently exposes `list_classes`.

## Modules

- `jaroscope-core`: archive indexing and bytecode-based class-interface extraction.
- `jaroscope-config`: typed YAML configuration and JAR path policy.
- `jaroscope-logging`: stderr-only compact logging for the stdio application.
- `jaroscope-decompiler`: reserved for a decompiler adapter and cache.
- `jaroscope-mcp`: stdio server and MCP tools.
- `buildSrc`: shared Java toolchain, JUnit and Google Java Style via Spotless.

The index selects the highest available class version no greater than the
requested Java release, provided the manifest declares `Multi-Release: true`.
It does not load JAR classes. Future work includes decompilation and a
configurable cache under `~/.jaroscope/cache`.
See [JAR indexing internals](docs/jar-index.md) for the current selection logic.
See [class-interface extraction](docs/class-interface.md) for bytecode metadata extraction.
See [configuration and path policy](docs/configuration.md) for configuration layering and access checks.
See [logging](docs/logging.md) for the stderr format and stream boundary.
See [application flow](docs/application.md) for startup and transport behavior.
See [`list_classes`](docs/tools/list-classes.md) for the class discovery tool contract.
See [`get_class_interface`](docs/tools/get-class-interface.md) for interface extraction.

## Build and test

Run `gradle check spotlessCheck`. `gradle spotlessApply` formats Java sources
and Gradle Kotlin scripts. The core and MCP tests use prebundled multi-release
JARs; see `jaroscope-core/src/test/fixture-sources/README.md` to regenerate it.
Testament produces a cross-module test summary when tests run.

Build and run the stdio server with `gradle :jaroscope-mcp:installDist`, then
execute `jaroscope-mcp/build/install/jaroscope-mcp/bin/jaroscope-mcp`. Use
`--config /path/to/application.yml` to supply an explicit configuration file.

The project's license has not yet been selected.
