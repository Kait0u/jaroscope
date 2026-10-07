plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "jaroscope"

include(
    "jaroscope-core",
    "jaroscope-config",
    "jaroscope-cache",
    "jaroscope-logging",
    "jaroscope-decompiler",
    "jaroscope-mcp",
)
