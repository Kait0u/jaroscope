plugins {
  id("buildlogic.java-application-conventions")
}

dependencies {
  implementation(project(":jaroscope-config"))
  implementation(project(":jaroscope-core"))
  implementation(project(":jaroscope-decompiler"))
}
