plugins {
  id("buildlogic.java-library-conventions")
}

dependencies {
  api(libs.slf4j.api)
  implementation(libs.logback.classic)
}
