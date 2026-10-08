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

/** Verifies bounded whole-JAR decompilation through its MCP tool contract. */
class DecompileJarToolTest {
  @TempDir Path temporaryDirectory;

  /** Locates the committed MCP fixture JAR. */
  private static Path fixture() throws URISyntaxException {
    return Path.of(DecompileJarToolTest.class.getResource("/fixtures/multi-release.jar").toURI());
  }

  /** Confirms that the tool returns a bounded summary without source bodies. */
  @Test
  void returnsBulkSummary() throws Exception {
    DecompileJarTool tool =
        new DecompileJarTool(
            new JaroscopeConfiguration(
                21, temporaryDirectory, Duration.ofDays(30), 1024L, List.of()));
    BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult> handler =
        tool.specification().callHandler();

    McpSchema.CallToolResult result =
        handler.apply(
            null,
            McpSchema.CallToolRequest.builder(McpToolConstants.DECOMPILE_JAR_TOOL)
                .arguments(
                    Map.of(
                        McpToolConstants.JAR_PATH_ARGUMENT,
                        fixture().toString(),
                        McpToolConstants.PACKAGE_PREFIX_ARGUMENT,
                        "example.",
                        McpToolConstants.MAX_CLASSES_ARGUMENT,
                        1))
                .build());

    assertFalse(result.isError());
    assertTrue(result.structuredContent().toString().contains("truncated=true"));
    assertTrue(result.structuredContent().toString().contains("decompiled=1"));
    assertFalse(result.structuredContent().toString().contains("public class Greeter"));

    McpSchema.CallToolResult repeatedResult =
        handler.apply(
            null,
            McpSchema.CallToolRequest.builder(McpToolConstants.DECOMPILE_JAR_TOOL)
                .arguments(
                    Map.of(
                        McpToolConstants.JAR_PATH_ARGUMENT,
                        fixture().toString(),
                        McpToolConstants.PACKAGE_PREFIX_ARGUMENT,
                        "example.",
                        McpToolConstants.MAX_CLASSES_ARGUMENT,
                        1))
                .build());

    assertTrue(repeatedResult.structuredContent().toString().contains("cacheHits=1"));
    assertTrue(repeatedResult.structuredContent().toString().contains("decompiled=0"));
  }
}
