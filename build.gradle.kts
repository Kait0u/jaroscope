plugins {
  base
  id("com.diffplug.spotless")
  alias(libs.plugins.testament)
}

allprojects {
  group = "pl.kaitou-dev"
  version = "0.1.0-SNAPSHOT"
}

repositories {
  mavenCentral()
}

spotless {
  kotlinGradle {
    target(
        "*.gradle.kts",
        "buildSrc/*.gradle.kts",
        "buildSrc/src/main/kotlin/*.gradle.kts",
        "jaroscope-*/build.gradle.kts",
    )
    ktfmt()
  }
}

tasks.named("check") {
  dependsOn(subprojects.map { "${it.path}:check" })
}
