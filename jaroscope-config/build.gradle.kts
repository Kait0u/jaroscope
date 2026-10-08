plugins {
  id("buildlogic.java-library-conventions")
}

dependencies {
  api(project(":jaroscope-core"))
  implementation(libs.jackson.yaml)
}
