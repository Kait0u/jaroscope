package pl.kaitou_dev.jaroscope.mcp;

/** Names, arguments, and bounds shared by JARoscope MCP tools. */
public final class McpToolConstants {
  /** The MCP tool name for class discovery. */
  public static final String LIST_CLASSES_TOOL = "list_classes";

  /** The MCP tool name for class-interface extraction. */
  public static final String GET_CLASS_INTERFACE_TOOL = "get_class_interface";

  /** The MCP tool name for source extraction. */
  public static final String GET_CLASS_SOURCE_TOOL = "get_class_source";

  /** The required JAR path argument. */
  public static final String JAR_PATH_ARGUMENT = "jarPath";

  /** The optional package prefix argument. */
  public static final String PACKAGE_PREFIX_ARGUMENT = "packagePrefix";

  /** The required binary class name argument. */
  public static final String CLASS_NAME_ARGUMENT = "className";

  /** The optional Java release argument. */
  public static final String TARGET_RELEASE_ARGUMENT = "targetRelease";

  /** The optional result limit argument. */
  public static final String MAX_RESULTS_ARGUMENT = "maxResults";

  /** The optional inherited-member resolution argument. */
  public static final String INCLUDE_INHERITED_ARGUMENT = "includeInheritedMembers";

  /** The default maximum number of classes returned by one request. */
  public static final int DEFAULT_MAX_RESULTS = 100;

  /** The absolute maximum number of classes returned by one request. */
  public static final int MAX_RESULTS_LIMIT = 1000;

  private McpToolConstants() {
    throw new AssertionError("No instances");
  }
}
