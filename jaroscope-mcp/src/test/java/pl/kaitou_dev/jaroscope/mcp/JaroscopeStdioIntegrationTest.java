package pl.kaitou_dev.jaroscope.mcp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Verifies initialization and tool discovery through the real stdio process. */
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

  /** Confirms that the packaged entrypoint speaks MCP over stdin and stdout. */
  @Test
  void startsAndListsToolsOverStdio() throws Exception {
    Process process =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Duser.home=" + Path.of("build", "stdio-test-home").toAbsolutePath(),
                "-cp",
                System.getProperty("java.class.path"),
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

      String secondResponse = outputReader.readLine();
      String output = String.valueOf(firstResponse) + String.valueOf(secondResponse);

      assertTrue(output.contains("JARoscope"));
      assertTrue(output.contains(McpToolConstants.LIST_CLASSES_TOOL));
      assertTrue(output.contains(McpToolConstants.GET_CLASS_INTERFACE_TOOL));
      assertTrue(output.contains(McpToolConstants.GET_CLASS_SOURCE_TOOL));
      assertTrue(output.contains(McpToolConstants.CACHE_STATUS_TOOL));
      assertTrue(output.contains(McpToolConstants.CLEAN_CACHE_TOOL));
      assertTrue(output.contains(McpToolConstants.DECOMPILE_JAR_TOOL));
      assertFalse(output.contains("LoggerContext"));
      assertFalse(output.contains("Found logback-core"));
    } finally {
      process.destroy();
      process.waitFor(5, TimeUnit.SECONDS);
    }
  }
}
