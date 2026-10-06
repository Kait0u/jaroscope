# JARoscope

JARoscope is a planned local stdio MCP server for exploring Java JARs from
clients such as Claude Code and OpenCode. This initial commit establishes a
Java 21 multi-project Gradle build and an independently testable JAR index.
**There is not yet a runnable MCP server or decompiler.**

## Modules

- `jaroscope-core`: archive indexing and multi-release class selection.
- `jaroscope-config`: typed YAML configuration and JAR path policy.
- `jaroscope-decompiler`: reserved for a decompiler adapter and cache.
- `jaroscope-mcp`: reserved for the stdio server and MCP tools.
- `buildSrc`: shared Java toolchain, JUnit and Google Java Style via Spotless.

The index selects the highest available class version no greater than the
requested Java release, provided the manifest declares `Multi-Release: true`.
It does not load JAR classes. Future work includes interface extraction,
decompilation, MCP tools, and a configurable cache under `~/.jaroscope/cache`.
See [JAR indexing internals](docs/jar-index.md) for the current selection logic.
See [configuration and path policy](docs/configuration.md) for configuration layering and access checks.

## Build and test

Run `./gradlew check spotlessCheck`. `./gradlew spotlessApply` formats Java
sources and Gradle Kotlin scripts. The core tests use a prebundled multi-release
JAR; see `jaroscope-core/src/test/fixture-sources/README.md` to regenerate it.
Testament produces a cross-module test summary when tests run.

The project's license has not yet been selected.
