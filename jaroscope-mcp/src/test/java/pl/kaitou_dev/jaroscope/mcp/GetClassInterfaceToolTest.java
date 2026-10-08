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
import pl.kaitou_dev.jaroscope.config.JaroscopeConfiguration;

/** Verifies structured class-interface extraction through the MCP tool contract. */
class GetClassInterfaceToolTest {
  /** Locates the committed MCP fixture JAR. */
  private static Path fixture() throws URISyntaxException {
    return Path.of(
        GetClassInterfaceToolTest.class.getResource("/fixtures/multi-release.jar").toURI());
  }

  /** Confirms that the tool returns structured declared metadata and annotations. */
  @Test
  void returnsStructuredClassInterface() throws Exception {
    GetClassInterfaceTool tool =
        new GetClassInterfaceTool(
            new JaroscopeConfiguration(
                21, Path.of("cache"), Duration.ofDays(30), 1024L, List.of()));
    BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult> handler =
        tool.specification().callHandler();

    McpSchema.CallToolResult result =
        handler.apply(
            null,
            McpSchema.CallToolRequest.builder(McpToolConstants.GET_CLASS_INTERFACE_TOOL)
                .arguments(
                    Map.of(
                        McpToolConstants.JAR_PATH_ARGUMENT,
                        fixture().toString(),
                        McpToolConstants.CLASS_NAME_ARGUMENT,
                        "example.Greeter"))
                .build());

    assertFalse(result.isError());
    assertTrue(result.structuredContent().toString().contains("example.Greeter"));
    assertTrue(result.structuredContent().toString().contains("example.Marker"));
    assertFalse(result.structuredContent().toString().contains("inheritedGreeting"));
  }

  /** Confirms that inherited members are added only when explicitly requested. */
  @Test
  void optionallyIncludesInheritedMembers() throws Exception {
    GetClassInterfaceTool tool =
        new GetClassInterfaceTool(
            new JaroscopeConfiguration(
                21, Path.of("cache"), Duration.ofDays(30), 1024L, List.of()));
    BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult> handler =
        tool.specification().callHandler();
    McpSchema.CallToolResult result =
        handler.apply(
            null,
            McpSchema.CallToolRequest.builder(McpToolConstants.GET_CLASS_INTERFACE_TOOL)
                .arguments(
                    Map.of(
                        McpToolConstants.JAR_PATH_ARGUMENT,
                        fixture().toString(),
                        McpToolConstants.CLASS_NAME_ARGUMENT,
                        "example.Greeter",
                        McpToolConstants.INCLUDE_INHERITED_ARGUMENT,
                        true))
                .build());

    assertFalse(result.isError());
    assertTrue(result.structuredContent().toString().contains("inheritedGreeting"));
    assertTrue(result.structuredContent().toString().contains("contractGreeting"));
  }
}
