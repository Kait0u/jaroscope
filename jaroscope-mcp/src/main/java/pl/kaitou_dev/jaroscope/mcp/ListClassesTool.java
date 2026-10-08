package pl.kaitou_dev.jaroscope.mcp;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import pl.kaitou_dev.jaroscope.config.JarPathPolicy;
import pl.kaitou_dev.jaroscope.config.JaroscopeConfiguration;
import pl.kaitou_dev.jaroscope.core.JarIndex;

/** Provides the MCP tool for bounded, multi-release-aware class discovery. */
@Slf4j
public final class ListClassesTool {
  private final JaroscopeConfiguration configuration;
  private final JarPathPolicy pathPolicy;

  /** Creates a class-listing tool backed by validated application configuration. */
  public ListClassesTool(JaroscopeConfiguration configuration) {
    this.configuration = Objects.requireNonNull(configuration, "configuration");
    pathPolicy = new JarPathPolicy(configuration);
  }

  /**
   * Builds the MCP tool specification for class discovery.
   *
   * @return a synchronous MCP tool specification
   */
  public McpServerFeatures.SyncToolSpecification specification() {
    return McpServerFeatures.SyncToolSpecification.builder()
        .tool(
            McpSchema.Tool.builder(McpToolConstants.LIST_CLASSES_TOOL, inputSchema())
                .description("List class names visible in a local JAR.")
                .build())
        .callHandler((exchange, request) -> call(request.arguments()))
        .build();
  }

  /** Creates the JSON Schema input definition advertised to MCP clients. */
  private Map<String, Object> inputSchema() {
    return Map.of(
        "type", "object",
        "properties",
            Map.of(
                McpToolConstants.JAR_PATH_ARGUMENT,
                    Map.of("type", "string", "description", "Path to a local JAR file"),
                McpToolConstants.PACKAGE_PREFIX_ARGUMENT,
                    Map.of("type", "string", "description", "Optional binary package prefix"),
                McpToolConstants.TARGET_RELEASE_ARGUMENT, Map.of("type", "integer", "minimum", 1),
                McpToolConstants.MAX_RESULTS_ARGUMENT,
                    Map.of(
                        "type",
                        "integer",
                        "minimum",
                        1,
                        "maximum",
                        McpToolConstants.MAX_RESULTS_LIMIT)),
        "required", List.of(McpToolConstants.JAR_PATH_ARGUMENT));
  }

  /** Executes one class-listing request and converts domain failures to tool errors. */
  private McpSchema.CallToolResult call(Map<String, Object> arguments) {
    try {
      Path requestedPath = Path.of(requiredString(arguments, McpToolConstants.JAR_PATH_ARGUMENT));
      Path jarPath = pathPolicy.validate(requestedPath);
      int targetRelease =
          integerArgument(
              arguments, McpToolConstants.TARGET_RELEASE_ARGUMENT, configuration.targetRelease());
      String packagePrefix =
          stringArgument(arguments, McpToolConstants.PACKAGE_PREFIX_ARGUMENT, "");
      int maxResults =
          integerArgument(
              arguments,
              McpToolConstants.MAX_RESULTS_ARGUMENT,
              McpToolConstants.DEFAULT_MAX_RESULTS);
      JarIndex index = JarIndex.open(jarPath, targetRelease, configuration.archiveLimits());
      List<String> matchingClasses =
          index.classes().keySet().stream()
              .filter(className -> className.startsWith(packagePrefix))
              .limit((long) maxResults + 1L)
              .toList();
      boolean truncated = matchingClasses.size() > maxResults;
      List<String> classes = truncated ? matchingClasses.subList(0, maxResults) : matchingClasses;
      log.info(
          "Listed classes jar={} targetRelease={} count={} truncated={}",
          jarPath,
          targetRelease,
          classes.size(),
          truncated);
      String response =
          "targetRelease="
              + targetRelease
              + "\n"
              + "count="
              + classes.size()
              + "\n"
              + "truncated="
              + truncated
              + "\n"
              + classes.stream().collect(Collectors.joining("\n"));
      return McpSchema.CallToolResult.builder()
          .content(List.of(McpSchema.TextContent.builder(response).build()))
          .isError(false)
          .build();
    } catch (IOException | RuntimeException exception) {
      log.warn("Class listing failed: {}", exception.getMessage());
      return McpSchema.CallToolResult.builder()
          .content(List.of(McpSchema.TextContent.builder(exception.getMessage()).build()))
          .isError(true)
          .build();
    }
  }

  /** Reads a non-blank required string argument. */
  private String requiredString(Map<String, Object> arguments, String name) {
    String value = stringArgument(arguments, name, null);
    if (value == null || value.isBlank()) {
      throw new ListClassesException("Missing required argument: " + name);
    }
    return value;
  }

  /** Reads a string argument or returns its configured default. */
  private String stringArgument(Map<String, Object> arguments, String name, String defaultValue) {
    Object value = arguments.get(name);
    return value == null ? defaultValue : value.toString();
  }

  /** Reads and validates a positive integer argument with an optional result bound. */
  private int integerArgument(Map<String, Object> arguments, String name, int defaultValue) {
    Object value = arguments.get(name);
    if (value == null) {
      return defaultValue;
    }
    if (!(value instanceof Number number)) {
      throw new ListClassesException("Argument must be an integer: " + name);
    }
    int result = number.intValue();
    if (result < 1
        || name.equals(McpToolConstants.MAX_RESULTS_ARGUMENT)
            && result > McpToolConstants.MAX_RESULTS_LIMIT) {
      throw new ListClassesException("Argument is outside the supported range: " + name);
    }
    return result;
  }
}
