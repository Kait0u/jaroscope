# `get_class_source`

Returns decompiled Java source for one class. The request uses the configured
cache and invokes Vineflower only when the exact source identity is absent. On
success, it queues a bounded whole-JAR cache warm-up when background
decompilation is enabled.

## Request

```json
{
  "jarPath": "/tmp/example.jar",
  "className": "com.example.Widget",
  "targetRelease": 21
}
```

`jarPath` and `className` are required. `targetRelease` defaults to the
configured Java release. Source larger than the configured response limit is
returned as an MCP tool error. The default limit is 1 MiB.

## Response

```json
{
  "binaryName": "com.example.Widget",
  "targetRelease": 21,
  "engineVersion": "1.12.0",
  "backgroundWarmupQueued": true,
  "sourceBytes": 1842,
  "source": "package com.example;\n..."
}
```

```mermaid
sequenceDiagram
    participant Client
    participant MCP as JARoscope MCP server
    participant Cache as CacheStore
    participant Decompiler as CachingDecompiler
    participant VF as Vineflower
    participant Queue as Background coordinator
    Client->>MCP: tools/call get_class_source
    MCP->>Cache: lookup JAR and request identity
    alt cache hit
        Cache-->>MCP: source
    else cache miss
        MCP->>Decompiler: decompile request
        Decompiler->>VF: decompile JAR
        VF-->>Decompiler: source
        Decompiler->>Cache: atomic store
        Decompiler-->>MCP: source
    end
    opt background warm-up enabled and queue accepts job
        MCP->>Queue: schedule JAR and target release
    end
    MCP-->>Client: structured source response
```

The foreground source response does not wait for background decompilation.
Duplicate warm-up jobs for the same resolved JAR path, file size, modification
time, and target release coalesce.
If a requested class is not cached while warm-up runs, its foreground request
may decompile it separately. Cache writes remain serialized.
