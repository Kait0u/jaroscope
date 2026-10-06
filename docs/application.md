# Application

`JaroscopeBootstrap` configures Logback before loading the application class.
The application then loads layered configuration, creates the stdio transport,
registers tools, and waits for shutdown.

```mermaid
sequenceDiagram
    participant Process
    participant Bootstrap
    participant Config as ConfigurationLoader
    participant Logging as LoggingConfigurator
    participant MCP as MCP server
    Process->>Bootstrap: main(arguments)
    Bootstrap->>Logging: configure stderr logging
    Bootstrap->>MCP: load application class
    MCP->>Config: load home and explicit YAML
    Config-->>MCP: JaroscopeConfiguration
    MCP->>MCP: create stdio transport and register tools
    MCP-->>Process: consume stdin and write protocol to stdout
    Process-->>MCP: shutdown signal
    MCP->>MCP: close server and transport
```

Stdout carries MCP JSON-RPC only. Logback writes diagnostics to stderr.

Build the distribution with:

```text
gradle :jaroscope-mcp:installDist
```
