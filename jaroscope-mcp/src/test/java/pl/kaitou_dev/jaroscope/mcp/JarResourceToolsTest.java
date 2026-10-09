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
import pl.kaitou_dev.jaroscope.config.ConfigurationConstants;
import pl.kaitou_dev.jaroscope.config.JaroscopeConfiguration;

/** Verifies MCP tools for listing and reading resources from the bundled JAR. */
class JarResourceToolsTest {
  private static final String BINARY_RESOURCE = "example/binary.dat";
  private static final String MISSING_RESOURCE = "example/missing.txt";

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
    McpSchema.CallToolResult binaryResult = readResource(handler, BINARY_RESOURCE);

    assertFalse(textResult.isError());
    assertTrue(textResult.structuredContent().toString().contains("generation=java17"));
    assertFalse(binaryResult.isError());
    assertTrue(binaryResult.structuredContent().toString().contains("encoding=base64"));
    assertTrue(binaryResult.structuredContent().toString().contains("AQL/"));
  }

  /** Confirms that the MCP reader rejects entries above its configured raw-byte limit. */
  @Test
  void rejectsResourceAboveConfiguredSizeLimit() throws Exception {
    GetJarResourceTool tool = new GetJarResourceTool(configuration(2L, List.of()));
    BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult> handler =
        tool.specification().callHandler();

    McpSchema.CallToolResult result = readResource(handler, BINARY_RESOURCE);

    assertTrue(result.isError());
    assertTrue(result.content().toString().contains("configured read limit"));
  }

  /** Confirms that the MCP reader reports an error for a missing logical resource path. */
  @Test
  void reportsMissingResourceAsToolError() throws Exception {
    GetJarResourceTool tool = new GetJarResourceTool(configuration());
    BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult> handler =
        tool.specification().callHandler();

    McpSchema.CallToolResult result = readResource(handler, MISSING_RESOURCE);

    assertTrue(result.isError());
    assertTrue(result.content().toString().contains("Resource not found: " + MISSING_RESOURCE));
  }

  /** Confirms both resource tools reject JARs located below a configured banned root. */
  @Test
  void rejectsJarUnderBannedRootForBothResourceTools() throws Exception {
    Path jarPath = fixture();
    JaroscopeConfiguration configuration =
        configuration(
            ConfigurationConstants.DEFAULT_MAX_JAR_RESOURCE_BYTES, List.of(jarPath.getParent()));
    ListJarResourcesTool listTool = new ListJarResourcesTool(configuration);
    GetJarResourceTool getTool = new GetJarResourceTool(configuration);
    BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult>
        listHandler = listTool.specification().callHandler();
    BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult>
        getHandler = getTool.specification().callHandler();

    McpSchema.CallToolResult listResult =
        listHandler.apply(
            null,
            McpSchema.CallToolRequest.builder(McpToolConstants.LIST_JAR_RESOURCES_TOOL)
                .arguments(Map.of(McpToolConstants.JAR_PATH_ARGUMENT, jarPath.toString()))
                .build());
    McpSchema.CallToolResult getResult = readResource(getHandler, "example/config.properties");

    assertTrue(listResult.isError());
    assertTrue(listResult.content().toString().contains("under a banned root"));
    assertTrue(getResult.isError());
    assertTrue(getResult.content().toString().contains("under a banned root"));
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

  /** Creates test configuration with a resource-size cap and banned-root policy. */
  private JaroscopeConfiguration configuration(long maxJarResourceBytes, List<Path> bannedRoots) {
    return new JaroscopeConfiguration(
        21,
        temporaryDirectory,
        Duration.ofDays(30),
        1024L,
        bannedRoots,
        ConfigurationConstants.DEFAULT_MAX_SOURCE_RESPONSE_BYTES,
        true,
        ConfigurationConstants.DEFAULT_MAX_BACKGROUND_JARS,
        ConfigurationConstants.DEFAULT_MAX_BACKGROUND_CLASSES,
        ConfigurationConstants.DEFAULT_MAX_ARCHIVE_BYTES,
        ConfigurationConstants.DEFAULT_MAX_ARCHIVE_ENTRIES,
        ConfigurationConstants.DEFAULT_MAX_CLASS_FILE_BYTES,
        ConfigurationConstants.DEFAULT_MAX_EXPANDED_CLASS_BYTES,
        ConfigurationConstants.DEFAULT_MAX_CONCURRENT_VINEFLOWER_RUNS,
        ConfigurationConstants.DEFAULT_VINEFLOWER_THREADS_PER_RUN,
        maxJarResourceBytes);
  }
}
