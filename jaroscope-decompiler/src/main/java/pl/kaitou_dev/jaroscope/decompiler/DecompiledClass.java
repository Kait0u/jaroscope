package pl.kaitou_dev.jaroscope.decompiler;

/** Source produced by decompiling one selected class. */
public record DecompiledClass(String binaryName, int targetRelease, String source) {}
