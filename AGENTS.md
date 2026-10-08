# JARoscope agent notes

- `master` is the trunk. Commit each self-contained increment separately; a feature commit includes its implementation, focused tests, and applicable documentation. Keep unrelated maintenance in separate commits.
- After each commit, use this report structure and include concrete details:
  - **Outcome:** what is now implemented or changed, plus any important limitation.
  - **Commit:** short hash and exact commit subject.
  - **Changes:** bullets naming meaningful behavior, affected modules or docs, and notable tests. Avoid vague entries such as “updated files.”
  - **Validation:** exact command and result, including test count when available. State explicitly when tests were not run.
  - **Working tree:** clean, or list remaining changes.
  - **Next:** next planned increment, or state that none is proposed.
- Keep reports concise but specific. Do not claim behavior or validation that was not verified.
- Use the locally installed `gradle`, not `./gradlew`. Run `gradle check spotlessCheck` before committing; `gradle spotlessApply` formats Java and Gradle Kotlin scripts. For one test class: `gradle :jaroscope-core:test --tests 'pl.kaitou_dev.jaroscope.core.JarIndexTest'`.
- `jaroscope-core` indexes multi-release class entries and extracts declared class interfaces without loading classes. `jaroscope-mcp` runs the stdio server and currently exposes `list_classes`, `get_class_interface`, `get_class_source`, `cache_status`, `clean_cache`, and `decompile_jar`; `jaroscope-decompiler` has a Vineflower adapter and cache decorator. The core tests use a checked-in JAR; regenerate it with `sh jaroscope-core/src/test/fixture-sources/regenerate.sh`.
- `jaroscope-mcp` is the only runnable application module. Other JARoscope modules are libraries; consume external engines such as Vineflower through their Java APIs, never by launching their applications or subprocesses.
- Cache computations may duplicate, but writes to the same cache directory must be serialized across threads and processes. Never rely on atomic rename alone to prevent concurrent writers.
- Document implemented logic in short Markdown pages with Mermaid diagrams. Keep an application page, a separate cache cleanup page when built, and one page per MCP tool with request/response examples and a sequence diagram. Link pages from `README.md`. Use direct prose, no filler or em dashes. Never document unimplemented behavior as shipped.
- Agreed behavior: configurable Java release defaulting to 21; on-demand source decompilation queues configurable, bounded whole-JAR background cache warming; `decompile_jar` remains an explicit bulk operation. Configuration uses `application.yml` through a library, without Spring. Local JAR paths are allowed except under configurable banned roots. The home cache is pruned at 30 days or 2 GiB; source responses default to a 1 MiB cap. Inherited-member resolution is optional, off by default, and limited to the inspected JAR; missing parents produce partial results and warnings. Class-interface extraction defaults to public and protected declared members.
