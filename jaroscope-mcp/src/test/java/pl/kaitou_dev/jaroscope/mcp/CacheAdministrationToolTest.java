package pl.kaitou_dev.jaroscope.mcp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.kaitou_dev.jaroscope.cache.CacheStore;
import pl.kaitou_dev.jaroscope.config.JaroscopeConfiguration;

/** Verifies cache inspection and cleanup through MCP tool contracts. */
class CacheAdministrationToolTest {
  @TempDir Path temporaryDirectory;

  /** Confirms that cache status returns structured usage data. */
  @Test
  void returnsCacheStatus() {
    McpSchema.CallToolResult result =
        new CacheStatusTool(configuration())
            .specification()
            .callHandler()
            .apply(
                null,
                McpSchema.CallToolRequest.builder("cache_status").arguments(Map.of()).build());

    assertFalse(result.isError());
    assertTrue(result.structuredContent().toString().contains("entryCount"));
  }

  /** Confirms that clear-all removes only owned source entries. */
  @Test
  void clearsOwnedCacheEntries() throws Exception {
    Path jar =
        Path.of(
            CacheAdministrationToolTest.class.getResource("/fixtures/multi-release.jar").toURI());
    new CacheStore(configuration()).put(jar, "example.Greeter", 21, "1.12.0", "default", "source");
    BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult> handler =
        new CleanCacheTool(configuration()).specification().callHandler();

    McpSchema.CallToolResult result =
        handler.apply(
            null,
            McpSchema.CallToolRequest.builder("clean_cache")
                .arguments(Map.of("clearAll", true))
                .build());

    assertFalse(result.isError());
    assertTrue(result.structuredContent().toString().contains("removedEntries=1"));
  }

  /** Creates a temporary cache configuration for one test. */
  private JaroscopeConfiguration configuration() {
    return new JaroscopeConfiguration(
        21, temporaryDirectory, Duration.ofDays(30), 1024L, List.of());
  }
}
