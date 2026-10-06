import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
  java
  id("com.diffplug.spotless")
}

repositories {
  mavenCentral()
}

val libraries = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
  testImplementation(libraries.findLibrary("junit-jupiter").get())

  testRuntimeOnly(libraries.findLibrary("junit-platform-launcher").get())
}

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(21)
  }
}

spotless {
  java {
    googleJavaFormat()
    removeUnusedImports()
    formatAnnotations()
  }
}

tasks.named<Test>("test") {
  useJUnitPlatform()
}
