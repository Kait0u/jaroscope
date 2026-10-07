package pl.kaitou_dev.jaroscope.cache;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Snapshot of cache usage and cleanup eligibility. */
public record CacheStatus(
    Path directory,
    int entryCount,
    long totalBytes,
    Optional<Instant> oldestEntry,
    Optional<Instant> newestEntry,
    int expiredEntryCount,
    long excessBytes,
    long maxBytes,
    Duration maxAge) {}
