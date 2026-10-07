plugins {
  id("buildlogic.java-library-conventions")
}

dependencies {
  implementation(project(":jaroscope-cache"))
  implementation(libs.vineflower)
  implementation(project(":jaroscope-config"))
  implementation(project(":jaroscope-logging"))
  api(project(":jaroscope-core"))
  compileOnly(libs.lombok)
  annotationProcessor(libs.lombok)
}
