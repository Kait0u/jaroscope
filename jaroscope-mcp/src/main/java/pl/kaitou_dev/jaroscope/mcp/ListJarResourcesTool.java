package pl.kaitou_dev.jaroscope.mcp;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import pl.kaitou_dev.jaroscope.config.JarPathPolicy;
import pl.kaitou_dev.jaroscope.config.JaroscopeConfiguration;
import pl.kaitou_dev.jaroscope.core.JarResource;
import pl.kaitou_dev.jaroscope.core.JarResourceIndex;

/** Lists bounded non-class entries from a validated local JAR. */
@Slf4j
public final class ListJarResourcesTool {
  private final JaroscopeConfiguration configuration;
  private final JarPathPolicy pathPolicy;

  /** Creates the tool with application archive and path limits. */
  public ListJarResourcesTool(JaroscopeConfiguration configuration) {
    this.configuration = Objects.requireNonNull(configuration, "configuration");
    pathPolicy = new JarPathPolicy(configuration);
  }

  /** Builds the MCP tool specification. */
  public McpServerFeatures.SyncToolSpecification specification() {
    return McpServerFeatures.SyncToolSpecification.builder()
        .tool(
            McpSchema.Tool.builder(McpToolConstants.LIST_JAR_RESOURCES_TOOL, inputSchema())
                .description("List non-class resources available in a local JAR.")
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
                McpToolConstants.RESOURCE_PREFIX_ARGUMENT,
                    Map.of("type", "string", "description", "Optional JAR entry path prefix"),
                McpToolConstants.TARGET_RELEASE_ARGUMENT, Map.of("type", "integer", "minimum", 1),
                McpToolConstants.MAX_RESOURCES_ARGUMENT,
                    Map.of(
                        "type",
                        "integer",
                        "minimum",
                        1,
                        "maximum",
                        McpToolConstants.MAX_RESOURCES_LIMIT)),
        "required", List.of(McpToolConstants.JAR_PATH_ARGUMENT));
  }

  /** Executes one bounded resource-list request. */
  private McpSchema.CallToolResult call(Map<String, Object> arguments) {
    try {
      Path jarPath =
          pathPolicy.validate(
              Path.of(requiredString(arguments, McpToolConstants.JAR_PATH_ARGUMENT)));
      int targetRelease = targetRelease(arguments);
      String prefix = stringArgument(arguments, McpToolConstants.RESOURCE_PREFIX_ARGUMENT, "");
      int maxResults = maxResults(arguments);
      JarResourceIndex index =
          JarResourceIndex.open(jarPath, targetRelease, configuration.archiveLimits());
      List<JarResource> matchingResources =
          index.resources().values().stream()
              .filter(resource -> resource.name().startsWith(prefix))
              .limit((long) maxResults + 1L)
              .toList();
      boolean truncated = matchingResources.size() > maxResults;
      List<JarResource> resources =
          truncated ? matchingResources.subList(0, maxResults) : matchingResources;
      List<Map<String, Object>> resourceItems = new ArrayList<>();
      for (JarResource resource : resources) {
        resourceItems.add(
            Map.of(
                "name", resource.name(),
                "selectedVersion", resource.selectedVersion(),
                "sizeBytes", resource.sizeBytes()));
      }
      Map<String, Object> response =
          Map.of(
              "targetRelease", targetRelease,
              "count", resources.size(),
              "truncated", truncated,
              "resources", resourceItems);
      return McpSchema.CallToolResult.builder()
          .content(
              List.of(
                  McpSchema.TextContent.builder(
                          McpJsonDefaults.getMapper().writeValueAsString(response))
                      .build()))
          .structuredContent(response)
          .isError(false)
          .build();
    } catch (IOException | RuntimeException exception) {
      log.warn("JAR resource listing failed: {}", exception.getMessage());
      return toolError(exception.getMessage());
    }
  }

  /** Reads a non-blank required JAR path. */
  private String requiredString(Map<String, Object> arguments, String name) {
    Object value = arguments.get(name);
    if (!(value instanceof String text) || text.isBlank()) {
      throw new JarResourceToolException("Missing required argument: " + name);
    }
    return text;
  }

  /** Reads an optional string argument. */
  private String stringArgument(Map<String, Object> arguments, String name, String defaultValue) {
    Object value = arguments.get(name);
    if (value == null) {
      return defaultValue;
    }
    if (!(value instanceof String text)) {
      throw new JarResourceToolException("Argument must be a string: " + name);
    }
    return text;
  }

  /** Reads the requested Java release or configured default. */
  private int targetRelease(Map<String, Object> arguments) {
    return integerArgument(
        arguments,
        McpToolConstants.TARGET_RELEASE_ARGUMENT,
        configuration.targetRelease(),
        Integer.MAX_VALUE);
  }

  /** Reads the configured result bound or its tool default. */
  private int maxResults(Map<String, Object> arguments) {
    return integerArgument(
        arguments,
        McpToolConstants.MAX_RESOURCES_ARGUMENT,
        McpToolConstants.DEFAULT_MAX_RESOURCES,
        McpToolConstants.MAX_RESOURCES_LIMIT);
  }

  /** Validates a positive integer argument against its maximum. */
  private int integerArgument(
      Map<String, Object> arguments, String name, int defaultValue, int maximum) {
    Object value = arguments.get(name);
    if (value == null) {
      return defaultValue;
    }
    if (!(value instanceof Number number)) {
      throw new JarResourceToolException("Argument must be an integer: " + name);
    }
    long result;
    try {
      result = new BigDecimal(number.toString()).longValueExact();
    } catch (ArithmeticException | NumberFormatException exception) {
      throw new JarResourceToolException("Argument must be an integer: " + name);
    }
    if (result < 1L || result > maximum) {
      throw new JarResourceToolException("Argument is outside the supported range: " + name);
    }
    return (int) result;
  }

  /** Returns a recoverable MCP tool error. */
  private McpSchema.CallToolResult toolError(String message) {
    return McpSchema.CallToolResult.builder()
        .content(List.of(McpSchema.TextContent.builder(message).build()))
        .isError(true)
        .build();
  }
}
