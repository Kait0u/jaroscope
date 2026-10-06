plugins {
  id("buildlogic.java-library-conventions")
}

dependencies {
  implementation(project(":jaroscope-config"))
  implementation(project(":jaroscope-logging"))
  api(project(":jaroscope-core"))
}
