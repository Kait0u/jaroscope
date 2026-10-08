# `clean_cache`

Removes expired entries and enforces the configured size limit. With
`clearAll: true`, it removes all JARoscope-owned artifact directories and
legacy flat `.source` entries. It does not touch unrelated cache-root files.

```json
{
  "clearAll": false
}
```

```json
{
  "removedEntries": 3,
  "removedBytes": 18240
}
```

```mermaid
sequenceDiagram
    participant Client
    participant Tool as clean_cache
    participant Store as CacheStore
    Client->>Tool: tools/call
    alt clearAll is true
        Tool->>Store: clear()
    else normal cleanup
        Tool->>Store: cleanup()
    end
    Store-->>Tool: CacheCleanupResult
    Tool-->>Client: structured cleanup result
```
