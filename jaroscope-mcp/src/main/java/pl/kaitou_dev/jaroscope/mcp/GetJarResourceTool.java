package pl.kaitou_dev.jaroscope.mcp;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import pl.kaitou_dev.jaroscope.config.JarPathPolicy;
import pl.kaitou_dev.jaroscope.config.JaroscopeConfiguration;
import pl.kaitou_dev.jaroscope.core.JarResourceIndex;

/** Reads a bounded text or binary non-class resource from a validated local JAR. */
@Slf4j
public final class GetJarResourceTool {
  private static final String UTF8_ENCODING = "utf-8";
  private static final String BASE64_ENCODING = "base64";
  private static final String TEXT_MIME_TYPE = "text/plain; charset=utf-8";
  private static final String BINARY_MIME_TYPE = "application/octet-stream";

  private final JaroscopeConfiguration configuration;
  private final JarPathPolicy pathPolicy;

  /** Creates the resource reader with application path and size limits. */
  public GetJarResourceTool(JaroscopeConfiguration configuration) {
    this.configuration = Objects.requireNonNull(configuration, "configuration");
    pathPolicy = new JarPathPolicy(configuration);
  }

  /** Builds the MCP tool specification. */
  public McpServerFeatures.SyncToolSpecification specification() {
    return McpServerFeatures.SyncToolSpecification.builder()
        .tool(
            McpSchema.Tool.builder(McpToolConstants.GET_JAR_RESOURCE_TOOL, inputSchema())
                .description("Read a bounded non-class resource from a local JAR.")
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
                McpToolConstants.RESOURCE_NAME_ARGUMENT,
                    Map.of("type", "string", "description", "Logical JAR entry name"),
                McpToolConstants.TARGET_RELEASE_ARGUMENT, Map.of("type", "integer", "minimum", 1)),
        "required",
            List.of(McpToolConstants.JAR_PATH_ARGUMENT, McpToolConstants.RESOURCE_NAME_ARGUMENT));
  }

  /** Executes one resource read, choosing UTF-8 text or Base64 encoding by strict decoding. */
  private McpSchema.CallToolResult call(Map<String, Object> arguments) {
    try {
      Path jarPath =
          pathPolicy.validate(
              Path.of(requiredString(arguments, McpToolConstants.JAR_PATH_ARGUMENT)));
      String resourceName = requiredString(arguments, McpToolConstants.RESOURCE_NAME_ARGUMENT);
      int targetRelease = targetRelease(arguments);
      JarResourceIndex index =
          JarResourceIndex.open(jarPath, targetRelease, configuration.archiveLimits());
      byte[] bytes = index.read(resourceName, configuration.maxJarResourceBytes());
      String mimeType = mimeType(resourceName, bytes);
      String encoding;
      String content;
      try {
        content =
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        encoding = UTF8_ENCODING;
      } catch (CharacterCodingException exception) {
        content = Base64.getEncoder().encodeToString(bytes);
        encoding = BASE64_ENCODING;
      }
      Map<String, Object> response =
          Map.of(
              "resourceName", resourceName,
              "targetRelease", targetRelease,
              "mimeType", mimeType,
              "encoding", encoding,
              "sizeBytes", bytes.length,
              "content", content);
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
      log.warn("JAR resource read failed: {}", exception.getMessage());
      return McpSchema.CallToolResult.builder()
          .content(List.of(McpSchema.TextContent.builder(exception.getMessage()).build()))
          .isError(true)
          .build();
    }
  }

  /** Reads a non-blank required string argument. */
  private String requiredString(Map<String, Object> arguments, String name) {
    Object value = arguments.get(name);
    if (value == null || !(value instanceof String text) || text.isBlank()) {
      throw new JarResourceToolException("Missing required argument: " + name);
    }
    return text;
  }

  /** Reads the requested Java release or the configured default. */
  private int targetRelease(Map<String, Object> arguments) {
    Object value = arguments.get(McpToolConstants.TARGET_RELEASE_ARGUMENT);
    if (value == null) {
      return configuration.targetRelease();
    }
    if (!(value instanceof Number number)) {
      throw new JarResourceToolException("targetRelease must be a positive integer");
    }
    long release;
    try {
      release = new BigDecimal(number.toString()).longValueExact();
    } catch (ArithmeticException | NumberFormatException exception) {
      throw new JarResourceToolException("targetRelease must be a positive integer");
    }
    if (release < 1L || release > Integer.MAX_VALUE) {
      throw new JarResourceToolException("targetRelease must be a positive integer");
    }
    return (int) release;
  }

  /** Detects a common MIME type from extension and defaults based on content encoding. */
  private String mimeType(String resourceName, byte[] bytes) {
    String guessed = URLConnection.guessContentTypeFromName(resourceName);
    if (guessed != null && !BINARY_MIME_TYPE.equals(guessed)) {
      return guessed;
    }
    return isUtf8(bytes) ? TEXT_MIME_TYPE : BINARY_MIME_TYPE;
  }

  /** Checks whether bytes can be decoded as strict UTF-8 without replacing malformed input. */
  private boolean isUtf8(byte[] bytes) {
    try {
      StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes));
      return true;
    } catch (CharacterCodingException exception) {
      return false;
    }
  }
}
