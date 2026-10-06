plugins {
  id("buildlogic.java-application-conventions")
}

dependencies {
  implementation(project(":jaroscope-core"))
  implementation(project(":jaroscope-decompiler"))
}
