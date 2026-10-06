plugins {
  id("buildlogic.java-application-conventions")
}

dependencies {
  implementation(libs.mcp)
  implementation(project(":jaroscope-config"))
  implementation(project(":jaroscope-core"))
  implementation(project(":jaroscope-decompiler"))
  implementation(project(":jaroscope-logging"))
  compileOnly(libs.lombok)
  annotationProcessor(libs.lombok)
}

application {
  mainClass = "pl.kaitou_dev.jaroscope.mcp.JaroscopeBootstrap"
}
