plugins {
  id("buildlogic.java-application-conventions")
}

dependencies {
  implementation(libs.mcp)
  implementation(project(":jaroscope-config"))
  implementation(project(":jaroscope-core"))
  implementation(project(":jaroscope-decompiler"))
  implementation(libs.slf4j.api)
  implementation(libs.logback.classic)
  compileOnly(libs.lombok)
  annotationProcessor(libs.lombok)
}

application {
  mainClass = "pl.kaitou_dev.jaroscope.mcp.JaroscopeBootstrap"
}
