package pl.kaitou_dev.jaroscope.mcp;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import pl.kaitou_dev.jaroscope.cache.CacheStore;
import pl.kaitou_dev.jaroscope.config.JarPathPolicy;
import pl.kaitou_dev.jaroscope.config.JaroscopeConfiguration;
import pl.kaitou_dev.jaroscope.decompiler.CachingDecompiler;
import pl.kaitou_dev.jaroscope.decompiler.DecompiledClass;
import pl.kaitou_dev.jaroscope.decompiler.VineflowerDecompiler;

/** Provides the MCP tool for cached source extraction. */
@Slf4j
public final class GetClassSourceTool {
  private final JaroscopeConfiguration configuration;
  private final JarPathPolicy pathPolicy;
  private final CachingDecompiler decompiler;

  /** Creates a source tool backed by the configured cache and Vineflower. */
  public GetClassSourceTool(JaroscopeConfiguration configuration) {
    this.configuration = Objects.requireNonNull(configuration, "configuration");
    pathPolicy = new JarPathPolicy(configuration);
    decompiler = new CachingDecompiler(new VineflowerDecompiler(), new CacheStore(configuration));
  }

  /**
   * Builds the MCP tool specification for source extraction.
   *
   * @return a synchronous MCP tool specification
   */
  public McpServerFeatures.SyncToolSpecification specification() {
    return McpServerFeatures.SyncToolSpecification.builder()
        .tool(
            McpSchema.Tool.builder(McpToolConstants.GET_CLASS_SOURCE_TOOL, inputSchema())
                .description("Return decompiled Java source for one class in a local JAR.")
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
                McpToolConstants.TARGET_RELEASE_ARGUMENT, Map.of("type", "integer", "minimum", 1)),
        "required",
            List.of(McpToolConstants.JAR_PATH_ARGUMENT, McpToolConstants.CLASS_NAME_ARGUMENT));
  }

  /** Executes one source request and converts domain failures to tool errors. */
  private McpSchema.CallToolResult call(Map<String, Object> arguments) {
    try {
      Path jarPath =
          pathPolicy.validate(
              Path.of(requiredString(arguments, McpToolConstants.JAR_PATH_ARGUMENT)));
      String className = requiredString(arguments, McpToolConstants.CLASS_NAME_ARGUMENT);
      int targetRelease = targetRelease(arguments);
      DecompiledClass result = decompiler.decompile(jarPath, className, targetRelease);
      long sourceBytes = result.source().getBytes(StandardCharsets.UTF_8).length;
      if (sourceBytes > configuration.maxSourceResponseBytes()) {
        throw new GetClassSourceException(
            "Decompiled source exceeds the configured response limit of "
                + configuration.maxSourceResponseBytes()
                + " bytes");
      }
      Map<String, Object> response =
          Map.of(
              "binaryName", result.binaryName(),
              "targetRelease", result.targetRelease(),
              "engineVersion", CachingDecompiler.ENGINE_VERSION,
              "sourceBytes", sourceBytes,
              "source", result.source());
      String text = McpJsonDefaults.getMapper().writeValueAsString(response);
      log.info(
          "Returned class source class={} targetRelease={} bytes={}",
          className,
          targetRelease,
          sourceBytes);
      return McpSchema.CallToolResult.builder()
          .content(List.of(McpSchema.TextContent.builder(text).build()))
          .structuredContent(response)
          .isError(false)
          .build();
    } catch (IOException | RuntimeException exception) {
      log.warn("Class-source request failed: {}", exception.getMessage());
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
      throw new GetClassSourceException("Missing required argument: " + name);
    }
    return value.toString();
  }

  /** Reads the requested Java release or the configured default. */
  private int targetRelease(Map<String, Object> arguments) {
    Object value = arguments.get(McpToolConstants.TARGET_RELEASE_ARGUMENT);
    if (value == null) {
      return configuration.targetRelease();
    }
    if (!(value instanceof Number number) || number.intValue() < 1) {
      throw new GetClassSourceException("targetRelease must be a positive integer");
    }
    return number.intValue();
  }
}
