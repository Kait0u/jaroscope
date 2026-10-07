package pl.kaitou_dev.jaroscope.mcp;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import pl.kaitou_dev.jaroscope.cache.CacheCleanupResult;
import pl.kaitou_dev.jaroscope.cache.CacheStore;
import pl.kaitou_dev.jaroscope.config.JaroscopeConfiguration;

/** Provides the MCP tool for removing owned cache entries. */
public final class CleanCacheTool {
  private final CacheStore cache;

  /** Creates a cache-cleanup tool backed by application configuration. */
  public CleanCacheTool(JaroscopeConfiguration configuration) {
    Objects.requireNonNull(configuration, "configuration");
    cache = new CacheStore(configuration);
  }

  /** Builds the MCP tool specification. */
  public McpServerFeatures.SyncToolSpecification specification() {
    return McpServerFeatures.SyncToolSpecification.builder()
        .tool(
            McpSchema.Tool.builder(
                    McpToolConstants.CLEAN_CACHE_TOOL,
                    Map.of(
                        "type",
                        "object",
                        "properties",
                        Map.of(
                            McpToolConstants.CLEAR_ALL_ARGUMENT,
                            Map.of("type", "boolean", "default", false))))
                .description("Remove expired or all JARoscope-owned cache entries.")
                .build())
        .callHandler((exchange, request) -> call(request.arguments()))
        .build();
  }

  private McpSchema.CallToolResult call(Map<String, Object> arguments) {
    try {
      boolean clearAll = Boolean.TRUE.equals(arguments.get(McpToolConstants.CLEAR_ALL_ARGUMENT));
      CacheCleanupResult result = clearAll ? cache.clear() : cache.cleanup();
      Map<String, Object> response =
          Map.of("removedEntries", result.removedEntries(), "removedBytes", result.removedBytes());
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
      return McpSchema.CallToolResult.builder()
          .content(List.of(McpSchema.TextContent.builder(exception.getMessage()).build()))
          .isError(true)
          .build();
    }
  }
}
