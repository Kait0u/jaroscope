package pl.kaitou_dev.jaroscope.cache;

/** Result of removing owned cache entries. */
public record CacheCleanupResult(int removedEntries, long removedBytes) {}
