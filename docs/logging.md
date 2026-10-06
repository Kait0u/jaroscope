# Logging

JARoscope reserves stdout for MCP JSON-RPC messages. Diagnostics use stderr.
The default format is compact and human-readable:

```text
2026-10-06 18:42:11.284 INFO  [main] jaroscope.application - Configuration loaded targetRelease=21
2026-10-06 18:42:12.004 WARN  [mcp-1] jaroscope.cache - Cache miss key=ab12... reason=source-not-present
```

ANSI colors are enabled only when color mode is `AUTO`, stderr is the process
stderr stream, and the process has an interactive console.

```mermaid
flowchart LR
    A[Application log] --> B[JDK Logger]
    B --> C[Root handler]
    C --> D[Compact formatter]
    D --> E[stderr]
    F[MCP protocol response] --> G[stdout]
```

The logging module has no runtime logging dependency. Consumers call
`LoggingConfigurator.configure(...)` during startup, then use JDK loggers.
