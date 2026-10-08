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
import pl.kaitou_dev.jaroscope.cache.CacheStore;
import pl.kaitou_dev.jaroscope.config.JarPathPolicy;
import pl.kaitou_dev.jaroscope.config.JaroscopeConfiguration;
import pl.kaitou_dev.jaroscope.core.JarIndex;
import pl.kaitou_dev.jaroscope.decompiler.CachingDecompiler;
import pl.kaitou_dev.jaroscope.decompiler.DecompilationBatchResult;
import pl.kaitou_dev.jaroscope.decompiler.VineflowerDecompiler;

/** Provides an explicit, bounded operation that decompiles visible classes into the cache. */
@Slf4j
public final class DecompileJarTool {
  private final JaroscopeConfiguration configuration;
  private final JarPathPolicy pathPolicy;

  /** Creates a bulk decompilation tool backed by application configuration. */
  public DecompileJarTool(JaroscopeConfiguration configuration) {
    this.configuration = Objects.requireNonNull(configuration, "configuration");
    pathPolicy = new JarPathPolicy(configuration);
  }

  /** Builds the MCP tool specification. */
  public McpServerFeatures.SyncToolSpecification specification() {
    return McpServerFeatures.SyncToolSpecification.builder()
        .tool(
            McpSchema.Tool.builder(McpToolConstants.DECOMPILE_JAR_TOOL, inputSchema())
                .description("Decompile visible classes in a local JAR into the JARoscope cache.")
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
                McpToolConstants.MAX_CLASSES_ARGUMENT,
                    Map.of(
                        "type",
                        "integer",
                        "minimum",
                        1,
                        "maximum",
                        McpToolConstants.MAX_CLASSES_LIMIT)),
        "required", List.of(McpToolConstants.JAR_PATH_ARGUMENT));
  }

  /** Executes one bounded bulk request and returns summary metadata only. */
  private McpSchema.CallToolResult call(Map<String, Object> arguments) {
    try {
      Path jarPath =
          pathPolicy.validate(
              Path.of(requiredString(arguments, McpToolConstants.JAR_PATH_ARGUMENT)));
      int targetRelease =
          integerArgument(
              arguments, McpToolConstants.TARGET_RELEASE_ARGUMENT, configuration.targetRelease());
      String packagePrefix =
          stringArgument(arguments, McpToolConstants.PACKAGE_PREFIX_ARGUMENT, "");
      int maxClasses =
          integerArgument(
              arguments,
              McpToolConstants.MAX_CLASSES_ARGUMENT,
              McpToolConstants.DEFAULT_MAX_CLASSES);

      JarIndex index = JarIndex.open(jarPath, targetRelease);
      List<String> matchingClasses =
          index.classes().keySet().stream()
              .filter(className -> className.startsWith(packagePrefix))
              .limit((long) maxClasses + 1L)
              .toList();
      boolean truncated = matchingClasses.size() > maxClasses;
      List<String> classes = truncated ? matchingClasses.subList(0, maxClasses) : matchingClasses;

      CacheStore cache = new CacheStore(configuration);
      CachingDecompiler decompiler = new CachingDecompiler(new VineflowerDecompiler(), cache);
      DecompilationBatchResult batch = decompiler.decompileClasses(jarPath, classes, targetRelease);
      int failed = batch.failedClasses().size();
      List<String> failureDetails = new ArrayList<>();
      for (String className : batch.failedClasses()) {
        if (failureDetails.size() < McpToolConstants.MAX_FAILURE_DETAILS) {
          failureDetails.add(className + ": Vineflower emitted no source");
        }
      }

      Map<String, Object> response =
          Map.of(
              "jar",
              jarPath.toString(),
              "targetRelease",
              targetRelease,
              "classCount",
              classes.size(),
              "cacheHits",
              batch.cacheHits(),
              "decompiled",
              batch.decompiled(),
              "failed",
              failed,
              "truncated",
              truncated,
              "failureDetails",
              failureDetails);
      log.info(
          "Bulk decompilation complete jar={} classes={} cacheHits={} decompiled={} failed={} truncated={}",
          jarPath,
          classes.size(),
          batch.cacheHits(),
          batch.decompiled(),
          failed,
          truncated);
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
      log.warn("Bulk decompilation failed: {}", exception.getMessage());
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
      throw new DecompileJarException("Missing required argument: " + name);
    }
    return value.toString();
  }

  /** Reads a string argument or returns its default. */
  private String stringArgument(Map<String, Object> arguments, String name, String defaultValue) {
    Object value = arguments.get(name);
    return value == null ? defaultValue : value.toString();
  }

  /** Reads and validates a positive integer argument, including the bulk class bound. */
  private int integerArgument(Map<String, Object> arguments, String name, int defaultValue) {
    Object value = arguments.get(name);
    if (value == null) {
      return defaultValue;
    }
    if (!(value instanceof Number number)) {
      throw new DecompileJarException("Argument must be an integer: " + name);
    }
    int result = number.intValue();
    if (result < 1
        || name.equals(McpToolConstants.MAX_CLASSES_ARGUMENT)
            && result > McpToolConstants.MAX_CLASSES_LIMIT) {
      throw new DecompileJarException("Argument is outside the supported range: " + name);
    }
    return result;
  }
}
