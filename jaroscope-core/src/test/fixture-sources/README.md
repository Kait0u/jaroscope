# Test JAR fixture

`src/test/resources/fixtures/multi-release.jar` is committed so tests use a real,
prebundled artifact rather than rebuilding one during every test run. The sources
here document its contents, including multi-release classes, inheritance, and
runtime annotations. Regenerate it with JDK 21 from the repository root:

`sh jaroscope-core/src/test/fixture-sources/regenerate.sh`

The script fixes archive timestamps for reproducible fixture bytes.
