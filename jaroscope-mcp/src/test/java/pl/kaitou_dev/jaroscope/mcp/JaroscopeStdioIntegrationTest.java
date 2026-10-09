package pl.kaitou_dev.jaroscope.mcp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.modelcontextprotocol.json.McpJsonDefaults;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies MCP tool calls through the installed distribution and stdio transport. */
class JaroscopeStdioIntegrationTest {
  /** JSON-RPC initialization request sent to the child process. */
  private static final String INITIALIZE_REQUEST =
      "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{"
          + "\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},"
          + "\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}";

  /** JSON-RPC notification completing MCP initialization. */
  private static final String INITIALIZED_NOTIFICATION =
      "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\",\"params\":{}}";

  /** JSON-RPC request listing the server tools. */
  private static final String TOOLS_LIST_REQUEST =
      "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}";

  @TempDir Path temporaryDirectory;

  /** Confirms that multiple tools work through one installed stdio server session. */
  @Test
  void callsJarToolsOverStdioWithoutPollutingProtocolOutput() throws Exception {
    Process process =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Duser.home=" + temporaryDirectory.toAbsolutePath(),
                "-cp",
                Path.of("build", "install", "jaroscope-mcp", "lib", "*")
                    .toAbsolutePath()
                    .toString(),
                JaroscopeBootstrap.class.getName())
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start();
    try {
      process.getOutputStream().write((INITIALIZE_REQUEST + "\n").getBytes(StandardCharsets.UTF_8));
      process.getOutputStream().flush();
      BufferedReader outputReader =
          new BufferedReader(
              new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
      String firstResponse = outputReader.readLine();
      process
          .getOutputStream()
          .write((INITIALIZED_NOTIFICATION + "\n").getBytes(StandardCharsets.UTF_8));
      process.getOutputStream().write((TOOLS_LIST_REQUEST + "\n").getBytes(StandardCharsets.UTF_8));
      process.getOutputStream().flush();

      String initializeResponse = String.valueOf(firstResponse);
      assertTrue(initializeResponse.startsWith("{"));
      assertTrue(initializeResponse.contains("JARoscope"));
      assertFalse(initializeResponse.contains("LoggerContext"));

      String toolsResponse = outputReader.readLine();
      assertTrue(toolsResponse.startsWith("{"));
      assertTrue(toolsResponse.contains(McpToolConstants.LIST_CLASSES_TOOL));
      assertTrue(toolsResponse.contains(McpToolConstants.GET_CLASS_INTERFACE_TOOL));
      assertTrue(toolsResponse.contains(McpToolConstants.GET_CLASS_SOURCE_TOOL));
      assertTrue(toolsResponse.contains(McpToolConstants.CACHE_STATUS_TOOL));
      assertTrue(toolsResponse.contains(McpToolConstants.CLEAN_CACHE_TOOL));
      assertTrue(toolsResponse.contains(McpToolConstants.DECOMPILE_JAR_TOOL));
      assertTrue(toolsResponse.contains(McpToolConstants.LIST_JAR_RESOURCES_TOOL));
      assertTrue(toolsResponse.contains(McpToolConstants.GET_JAR_RESOURCE_TOOL));

      Path fixture = fixture();
      String classListResponse =
          callTool(
              process,
              outputReader,
              3,
              McpToolConstants.LIST_CLASSES_TOOL,
              Map.of(McpToolConstants.JAR_PATH_ARGUMENT, fixture.toString()));
      assertTrue(classListResponse.contains("example.Greeter"));

      String interfaceResponse =
          callTool(
              process,
              outputReader,
              4,
              McpToolConstants.GET_CLASS_INTERFACE_TOOL,
              Map.of(
                  McpToolConstants.JAR_PATH_ARGUMENT,
                  fixture.toString(),
                  McpToolConstants.CLASS_NAME_ARGUMENT,
                  "example.Greeter",
                  McpToolConstants.INCLUDE_INHERITED_ARGUMENT,
                  true));
      assertTrue(interfaceResponse.contains("inheritedGreeting"));

      String sourceResponse =
          callTool(
              process,
              outputReader,
              5,
              McpToolConstants.GET_CLASS_SOURCE_TOOL,
              Map.of(
                  McpToolConstants.JAR_PATH_ARGUMENT,
                  fixture.toString(),
                  McpToolConstants.CLASS_NAME_ARGUMENT,
                  "example.Greeter"));
      assertTrue(sourceResponse.contains("class Greeter"));

      String cacheStatusResponse =
          callTool(process, outputReader, 6, McpToolConstants.CACHE_STATUS_TOOL, Map.of());
      assertTrue(cacheStatusResponse.contains("entryCount"));

      String resourceListResponse =
          callTool(
              process,
              outputReader,
              7,
              McpToolConstants.LIST_JAR_RESOURCES_TOOL,
              Map.of(
                  McpToolConstants.JAR_PATH_ARGUMENT,
                  fixture.toString(),
                  McpToolConstants.RESOURCE_PREFIX_ARGUMENT,
                  "example/"));
      assertTrue(resourceListResponse.contains("example/config.properties"));

      String textResourceResponse =
          callTool(
              process,
              outputReader,
              8,
              McpToolConstants.GET_JAR_RESOURCE_TOOL,
              Map.of(
                  McpToolConstants.JAR_PATH_ARGUMENT,
                  fixture.toString(),
                  McpToolConstants.RESOURCE_NAME_ARGUMENT,
                  "example/config.properties",
                  McpToolConstants.TARGET_RELEASE_ARGUMENT,
                  21));
      assertTrue(textResourceResponse.contains("generation=java17"));

      String binaryResourceResponse =
          callTool(
              process,
              outputReader,
              9,
              McpToolConstants.GET_JAR_RESOURCE_TOOL,
              Map.of(
                  McpToolConstants.JAR_PATH_ARGUMENT,
                  fixture.toString(),
                  McpToolConstants.RESOURCE_NAME_ARGUMENT,
                  "example/binary.dat"));
      assertTrue(binaryResourceResponse.contains("\"encoding\":\"base64\""));
      assertTrue(binaryResourceResponse.contains("AQL/"));
    } finally {
      process.destroy();
      boolean stopped = process.waitFor(15, TimeUnit.SECONDS);
      if (!stopped) {
        process.destroyForcibly();
        stopped = process.waitFor(5, TimeUnit.SECONDS);
      }
      assertTrue(stopped, "JARoscope child process did not stop after shutdown");
    }
  }

  /** Sends one MCP tool call and reads its matching single-line JSON-RPC response. */
  private String callTool(
      Process process,
      BufferedReader outputReader,
      int requestId,
      String toolName,
      Map<String, Object> arguments)
      throws Exception {
    String request =
        McpJsonDefaults.getMapper()
            .writeValueAsString(
                Map.of(
                    "jsonrpc",
                    "2.0",
                    "id",
                    requestId,
                    "method",
                    "tools/call",
                    "params",
                    Map.of("name", toolName, "arguments", arguments)));
    process.getOutputStream().write((request + "\n").getBytes(StandardCharsets.UTF_8));
    process.getOutputStream().flush();
    String response = outputReader.readLine();
    assertTrue(response != null && response.startsWith("{"));
    assertFalse(response.contains("\"isError\":true"));
    assertFalse(response.contains("Found logback-core"));
    assertFalse(response.contains("LoggerContext"));
    return response;
  }

  /** Locates the bundled fixture JAR used by the MCP process. */
  private Path fixture() throws Exception {
    return Path.of(
        JaroscopeStdioIntegrationTest.class.getResource("/fixtures/multi-release.jar").toURI());
  }
}
