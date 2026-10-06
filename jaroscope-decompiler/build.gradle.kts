plugins {
  id("buildlogic.java-library-conventions")
}

dependencies {
  implementation(project(":jaroscope-config"))
  api(project(":jaroscope-core"))
}
