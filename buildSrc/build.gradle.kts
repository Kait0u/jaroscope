plugins {
  `kotlin-dsl`
}

repositories {
  gradlePluginPortal()
}

dependencies {
  implementation(
      "com.diffplug.spotless:com.diffplug.spotless.gradle.plugin:${libs.versions.spotless.get()}"
  )
}
