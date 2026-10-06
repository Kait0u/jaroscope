plugins {
  java
  id("com.diffplug.spotless")
}

repositories {
  mavenCentral()
}

dependencies {
  testImplementation("org.junit.jupiter:junit-jupiter:6.0.1")

  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
