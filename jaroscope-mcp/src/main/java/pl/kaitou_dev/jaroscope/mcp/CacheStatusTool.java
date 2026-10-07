package pl.kaitou_dev.jaroscope.mcp;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import pl.kaitou_dev.jaroscope.cache.CacheStatus;
import pl.kaitou_dev.jaroscope.cache.CacheStore;
import pl.kaitou_dev.jaroscope.config.JaroscopeConfiguration;

/** Provides the MCP tool for inspecting cache usage. */
public final class CacheStatusTool {
  private final CacheStore cache;

  /** Creates a cache-status tool backed by application configuration. */
  public CacheStatusTool(JaroscopeConfiguration configuration) {
    Objects.requireNonNull(configuration, "configuration");
    cache = new CacheStore(configuration);
  }

  /** Builds the MCP tool specification. */
  public McpServerFeatures.SyncToolSpecification specification() {
    return McpServerFeatures.SyncToolSpecification.builder()
        .tool(
            McpSchema.Tool.builder(McpToolConstants.CACHE_STATUS_TOOL, Map.of("type", "object"))
                .description("Show JARoscope cache usage and cleanup eligibility.")
                .build())
        .callHandler((exchange, request) -> call())
        .build();
  }

  private McpSchema.CallToolResult call() {
    try {
      CacheStatus status = cache.status();
      Map<String, Object> response = new LinkedHashMap<>();
      response.put("directory", status.directory().toString());
      response.put("entryCount", status.entryCount());
      response.put("totalBytes", status.totalBytes());
      response.put("oldestEntry", status.oldestEntry().map(Object::toString).orElse(null));
      response.put("newestEntry", status.newestEntry().map(Object::toString).orElse(null));
      response.put("expiredEntryCount", status.expiredEntryCount());
      response.put("excessBytes", status.excessBytes());
      response.put("maxBytes", status.maxBytes());
      response.put("maxAge", status.maxAge().toString());
      return McpSchema.CallToolResult.builder()
          .content(
              List.of(
                  McpSchema.TextContent.builder(
                          McpJsonDefaults.getMapper().writeValueAsString(response))
                      .build()))
          .structuredContent(response)
          .isError(false)
          .build();
    } catch (IOException exception) {
      return error(exception.getMessage());
    }
  }

  private McpSchema.CallToolResult error(String message) {
    return McpSchema.CallToolResult.builder()
        .content(List.of(McpSchema.TextContent.builder(message).build()))
        .isError(true)
        .build();
  }
}
