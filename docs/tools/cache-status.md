# `cache_status`

Returns usage and cleanup eligibility for the JARoscope-owned cache.
`entryCount` and `totalBytes` describe cached `.java` source files. Artifact
metadata and cache lock files are not included in the byte budget.

```json
{
  "entryCount": 12,
  "totalBytes": 43821,
  "expiredEntryCount": 1,
  "excessBytes": 0,
  "maxBytes": 2147483648,
  "maxAge": "PT720H"
}
```

```mermaid
sequenceDiagram
    participant Client
    participant Tool as cache_status
    participant Store as CacheStore
    Client->>Tool: tools/call
    Tool->>Store: status()
    Store-->>Tool: CacheStatus
    Tool-->>Client: structured status
```
