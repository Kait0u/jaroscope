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

/** Verifies MCP tools for listing and reading resources from the bundled JAR. */
class JarResourceToolsTest {
  @TempDir Path temporaryDirectory;

  /** Locates the committed MCP fixture JAR. */
  private static Path fixture() throws URISyntaxException {
    return Path.of(JarResourceToolsTest.class.getResource("/fixtures/multi-release.jar").toURI());
  }

  /** Confirms that resource listing filters non-class paths and reports truncation. */
  @Test
  void listsFilteredJarResources() throws Exception {
    ListJarResourcesTool tool = new ListJarResourcesTool(configuration());
    BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult> handler =
        tool.specification().callHandler();

    McpSchema.CallToolResult result =
        handler.apply(
            null,
            McpSchema.CallToolRequest.builder(McpToolConstants.LIST_JAR_RESOURCES_TOOL)
                .arguments(
                    Map.of(
                        McpToolConstants.JAR_PATH_ARGUMENT,
                        fixture().toString(),
                        McpToolConstants.RESOURCE_PREFIX_ARGUMENT,
                        "example/",
                        McpToolConstants.MAX_RESOURCES_ARGUMENT,
                        1))
                .build());

    assertFalse(result.isError());
    assertTrue(result.structuredContent().toString().contains("truncated=true"));
    assertFalse(result.structuredContent().toString().contains("Greeter.class"));
  }

  /** Confirms that text resources use UTF-8 and binary resources use Base64. */
  @Test
  void readsTextAndBinaryResources() throws Exception {
    GetJarResourceTool tool = new GetJarResourceTool(configuration());
    BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult> handler =
        tool.specification().callHandler();

    McpSchema.CallToolResult textResult = readResource(handler, "example/config.properties");
    McpSchema.CallToolResult binaryResult = readResource(handler, "example/binary.dat");

    assertFalse(textResult.isError());
    assertTrue(textResult.structuredContent().toString().contains("generation=java17"));
    assertFalse(binaryResult.isError());
    assertTrue(binaryResult.structuredContent().toString().contains("encoding=base64"));
    assertTrue(binaryResult.structuredContent().toString().contains("AQL/"));
  }

  /** Calls the resource reader with the configured multi-release target. */
  private McpSchema.CallToolResult readResource(
      BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult>
          handler,
      String resourceName)
      throws Exception {
    return handler.apply(
        null,
        McpSchema.CallToolRequest.builder(McpToolConstants.GET_JAR_RESOURCE_TOOL)
            .arguments(
                Map.of(
                    McpToolConstants.JAR_PATH_ARGUMENT,
                    fixture().toString(),
                    McpToolConstants.RESOURCE_NAME_ARGUMENT,
                    resourceName,
                    McpToolConstants.TARGET_RELEASE_ARGUMENT,
                    21))
            .build());
  }

  /** Creates temporary configuration for tool tests. */
  private JaroscopeConfiguration configuration() {
    return new JaroscopeConfiguration(
        21, temporaryDirectory, Duration.ofDays(30), 1024L, List.of());
  }
}
