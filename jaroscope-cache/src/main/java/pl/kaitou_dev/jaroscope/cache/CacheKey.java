package pl.kaitou_dev.jaroscope.cache;

/** Immutable identity for one decompiled source result. */
public record CacheKey(
    String jarSha256,
    String binaryName,
    int targetRelease,
    String engineVersion,
    String optionsFingerprint) {}
