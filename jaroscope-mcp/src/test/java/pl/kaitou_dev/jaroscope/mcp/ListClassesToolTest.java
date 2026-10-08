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

/** Verifies the list-classes tool against the committed multi-release fixture. */
class ListClassesToolTest {
  private static final String FIRST_SORTED_CLASS = "example.BaseGreeter";

  /** Returns the committed fixture supplied by the core module. */
  private static Path fixture() throws URISyntaxException {
    return Path.of(ListClassesToolTest.class.getResource("/fixtures/multi-release.jar").toURI());
  }

  /** Confirms that class discovery returns bounded, target-release-aware text. */
  @Test
  void listsClassesWithBoundedResults() throws Exception {
    ListClassesTool tool =
        new ListClassesTool(
            new JaroscopeConfiguration(
                21, Path.of("cache"), Duration.ofDays(30), 1024L, List.of()));
    BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult> handler =
        tool.specification().callHandler();

    McpSchema.CallToolResult result =
        handler.apply(
            null,
            McpSchema.CallToolRequest.builder(McpToolConstants.LIST_CLASSES_TOOL)
                .arguments(
                    Map.of(
                        McpToolConstants.JAR_PATH_ARGUMENT,
                        fixture().toString(),
                        McpToolConstants.PACKAGE_PREFIX_ARGUMENT,
                        "example.",
                        McpToolConstants.MAX_RESULTS_ARGUMENT,
                        1))
                .build());
    String text = ((McpSchema.TextContent) result.content().get(0)).text();

    assertFalse(result.isError());
    assertTrue(text.contains("truncated=true"));
    assertTrue(text.contains(FIRST_SORTED_CLASS));
  }
}
