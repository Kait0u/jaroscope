#!/bin/sh
set -eu

cd "$(dirname "$0")/../../.."
mkdir -p build/fixture/base build/fixture/java17 src/test/resources/fixtures
javac --release 8 -d build/fixture/base src/test/fixture-sources/base/example/*.java
javac --release 17 -cp build/fixture/base -d build/fixture/java17 src/test/fixture-sources/java17/example/*.java
cp -R src/test/fixture-sources/resources/. build/fixture/base/
cp -R src/test/fixture-sources/java17-resources/. build/fixture/java17/
printf '\001\002\377' > build/fixture/base/example/binary.dat
jar --create --date=2020-01-01T00:00:00Z \
  --file src/test/resources/fixtures/multi-release.jar \
  --manifest src/test/fixture-sources/MANIFEST.MF \
  -C build/fixture/base . \
  --release 17 -C build/fixture/java17 .
