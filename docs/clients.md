# Agent clients

Build the local distribution once from the repository root:

```sh
gradle :jaroscope-mcp:installDist
```

Use the absolute path to the generated launcher. Rebuild after changing the
application or its runtime dependencies.

## Claude Code

Add this to the project's `.mcp.json`, replacing the launcher path. The config
file argument is optional.

```json
{
  "mcpServers": {
    "jaroscope": {
      "type": "stdio",
      "command": "/absolute/path/to/jaroscope-mcp/build/install/jaroscope-mcp/bin/jaroscope-mcp",
      "args": ["--config", "/absolute/path/to/application.yml"]
    }
  }
}
```

For a user-scoped server, add it with the Claude Code CLI:

```sh
claude mcp add --scope user jaroscope \
  -- /absolute/path/to/jaroscope-mcp/build/install/jaroscope-mcp/bin/jaroscope-mcp
```

## OpenCode

Add this server to `opencode.json` or `opencode.jsonc`:

```json
{
  "$schema": "https://opencode.ai/config.json",
  "mcp": {
    "jaroscope": {
      "type": "local",
      "command": [
        "/absolute/path/to/jaroscope-mcp/build/install/jaroscope-mcp/bin/jaroscope-mcp",
        "--config",
        "/absolute/path/to/application.yml"
      ],
      "enabled": true
    }
  }
}
```

Both clients launch JARoscope as a local stdio process. JARoscope writes MCP
messages to stdout and diagnostics to stderr. Keep the two streams separate.
