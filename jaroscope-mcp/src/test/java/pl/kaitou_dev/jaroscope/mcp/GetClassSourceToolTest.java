package pl.kaitou_dev.jaroscope.mcp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.kaitou_dev.jaroscope.config.JaroscopeConfiguration;
import pl.kaitou_dev.jaroscope.decompiler.BackgroundDecompilationCoordinator;

/** Verifies cached source extraction through the MCP tool contract. */
class GetClassSourceToolTest {
  @TempDir Path temporaryDirectory;

  /** Locates the committed MCP fixture JAR. */
  private static Path fixture() throws URISyntaxException {
    return Path.of(GetClassSourceToolTest.class.getResource("/fixtures/multi-release.jar").toURI());
  }

  /** Confirms that the tool returns structured decompiled source. */
  @Test
  void returnsStructuredClassSource() throws Exception {
    JaroscopeConfiguration configuration =
        new JaroscopeConfiguration(21, temporaryDirectory, Duration.ofDays(30), 1024L, List.of());
    try (BackgroundDecompilationCoordinator background =
        new BackgroundDecompilationCoordinator(configuration)) {
      GetClassSourceTool tool = new GetClassSourceTool(configuration, background);
      BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult>
          handler = tool.specification().callHandler();

      McpSchema.CallToolResult result =
          handler.apply(
              null,
              McpSchema.CallToolRequest.builder(McpToolConstants.GET_CLASS_SOURCE_TOOL)
                  .arguments(
                      Map.of(
                          McpToolConstants.JAR_PATH_ARGUMENT,
                          fixture().toString(),
                          McpToolConstants.CLASS_NAME_ARGUMENT,
                          "example.Greeter"))
                  .build());

      assertFalse(result.isError());
      assertTrue(result.structuredContent().toString().contains("class Greeter"));
      assertTrue(result.structuredContent().toString().contains("backgroundWarmupQueued=true"));
    }
  }
}
