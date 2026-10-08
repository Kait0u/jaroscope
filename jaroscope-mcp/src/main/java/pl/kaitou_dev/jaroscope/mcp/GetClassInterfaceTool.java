package pl.kaitou_dev.jaroscope.mcp;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import pl.kaitou_dev.jaroscope.config.JarPathPolicy;
import pl.kaitou_dev.jaroscope.config.JaroscopeConfiguration;
import pl.kaitou_dev.jaroscope.core.ClassInterface;
import pl.kaitou_dev.jaroscope.core.ClassInterfaceExtractor;
import pl.kaitou_dev.jaroscope.core.ClassInterfaceResult;

/** Provides the MCP tool for extracting one class's declared interface. */
@Slf4j
public final class GetClassInterfaceTool {
  private final JaroscopeConfiguration configuration;
  private final JarPathPolicy pathPolicy;
  private final ClassInterfaceExtractor extractor;

  /** Creates a class-interface tool backed by validated application configuration. */
  public GetClassInterfaceTool(JaroscopeConfiguration configuration) {
    this.configuration = Objects.requireNonNull(configuration, "configuration");
    pathPolicy = new JarPathPolicy(configuration);
    extractor = new ClassInterfaceExtractor();
  }

  /**
   * Builds the MCP tool specification for class-interface extraction.
   *
   * @return a synchronous MCP tool specification
   */
  public McpServerFeatures.SyncToolSpecification specification() {
    return McpServerFeatures.SyncToolSpecification.builder()
        .tool(
            McpSchema.Tool.builder(McpToolConstants.GET_CLASS_INTERFACE_TOOL, inputSchema())
                .description("Extract the declared interface of one class in a local JAR.")
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
                McpToolConstants.CLASS_NAME_ARGUMENT,
                    Map.of("type", "string", "description", "Binary class name"),
                McpToolConstants.TARGET_RELEASE_ARGUMENT, Map.of("type", "integer", "minimum", 1),
                McpToolConstants.INCLUDE_INHERITED_ARGUMENT,
                    Map.of("type", "boolean", "default", false)),
        "required",
            List.of(McpToolConstants.JAR_PATH_ARGUMENT, McpToolConstants.CLASS_NAME_ARGUMENT));
  }

  /** Executes one extraction request and converts domain failures to tool errors. */
  private McpSchema.CallToolResult call(Map<String, Object> arguments) {
    try {
      Path jarPath =
          pathPolicy.validate(
              Path.of(requiredString(arguments, McpToolConstants.JAR_PATH_ARGUMENT)));
      String className = requiredString(arguments, McpToolConstants.CLASS_NAME_ARGUMENT);
      int targetRelease =
          integerArgument(
              arguments, McpToolConstants.TARGET_RELEASE_ARGUMENT, configuration.targetRelease());
      boolean includeInherited =
          booleanArgument(arguments, McpToolConstants.INCLUDE_INHERITED_ARGUMENT, false);
      ClassInterfaceResult extraction =
          extractor.extractWithInheritance(
              jarPath, className, targetRelease, includeInherited, configuration.archiveLimits());
      ClassInterface classInterface = extraction.classInterface();
      List<String> warnings = new ArrayList<>(extraction.warnings());
      Map<String, Object> response =
          Map.of(
              "targetRelease", targetRelease,
              "includeInheritedMembers", includeInherited,
              "warnings", warnings,
              "classInterface", classInterface);
      String text = McpJsonDefaults.getMapper().writeValueAsString(response);
      log.info(
          "Extracted class interface jar={} class={} targetRelease={}",
          jarPath,
          className,
          targetRelease);
      return McpSchema.CallToolResult.builder()
          .content(List.of(McpSchema.TextContent.builder(text).build()))
          .structuredContent(response)
          .isError(false)
          .build();
    } catch (IOException | RuntimeException exception) {
      log.warn("Class-interface extraction failed: {}", exception.getMessage());
      return McpSchema.CallToolResult.builder()
          .content(List.of(McpSchema.TextContent.builder(exception.getMessage()).build()))
          .isError(true)
          .build();
    }
  }

  /** Reads a non-blank required string argument. */
  private String requiredString(Map<String, Object> arguments, String name) {
    Object value = arguments.get(name);
    if (value == null || value.toString().isBlank()) {
      throw new GetClassInterfaceException("Missing required argument: " + name);
    }
    return value.toString();
  }

  /** Reads and validates a positive integer argument. */
  private int integerArgument(Map<String, Object> arguments, String name, int defaultValue) {
    Object value = arguments.get(name);
    if (value == null) {
      return defaultValue;
    }
    if (!(value instanceof Number number)) {
      throw new GetClassInterfaceException("Argument must be an integer: " + name);
    }
    int result = number.intValue();
    if (result < 1) {
      throw new GetClassInterfaceException("Argument is outside the supported range: " + name);
    }
    return result;
  }

  /** Reads a boolean argument or returns its default. */
  private boolean booleanArgument(
      Map<String, Object> arguments, String name, boolean defaultValue) {
    Object value = arguments.get(name);
    if (value == null) {
      return defaultValue;
    }
    if (!(value instanceof Boolean result)) {
      throw new GetClassInterfaceException("Argument must be a boolean: " + name);
    }
    return result;
  }
}
