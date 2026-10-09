package pl.kaitou_dev.jaroscope.core;

/** A non-class resource visible in a JAR at one Java release. */
public record JarResource(String name, int selectedVersion, long sizeBytes) {}
