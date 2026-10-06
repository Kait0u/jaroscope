plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "jaroscope"

include("jaroscope-core", "jaroscope-decompiler", "jaroscope-mcp")
