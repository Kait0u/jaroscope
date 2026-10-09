package pl.kaitou_dev.jaroscope.mcp;

/** Names, arguments, and bounds shared by JARoscope MCP tools. */
public final class McpToolConstants {
  /** The MCP tool name for class discovery. */
  public static final String LIST_CLASSES_TOOL = "list_classes";

  /** The MCP tool name for class-interface extraction. */
  public static final String GET_CLASS_INTERFACE_TOOL = "get_class_interface";

  /** The MCP tool name for source extraction. */
  public static final String GET_CLASS_SOURCE_TOOL = "get_class_source";

  /** The MCP tool name for explicit whole-JAR decompilation. */
  public static final String DECOMPILE_JAR_TOOL = "decompile_jar";

  /** The MCP tool name for listing non-class JAR entries. */
  public static final String LIST_JAR_RESOURCES_TOOL = "list_jar_resources";

  /** The MCP tool name for reading one non-class JAR entry. */
  public static final String GET_JAR_RESOURCE_TOOL = "get_jar_resource";

  /** The MCP tool name for cache status. */
  public static final String CACHE_STATUS_TOOL = "cache_status";

  /** The MCP tool name for cache cleanup. */
  public static final String CLEAN_CACHE_TOOL = "clean_cache";

  /** The cleanup argument that removes all owned entries. */
  public static final String CLEAR_ALL_ARGUMENT = "clearAll";

  /** The optional class limit argument for bulk decompilation. */
  public static final String MAX_CLASSES_ARGUMENT = "maxClasses";

  /** Default class limit for one bulk request. */
  public static final int DEFAULT_MAX_CLASSES = 10_000;

  /** Absolute class limit for one bulk request. */
  public static final int MAX_CLASSES_LIMIT = 100_000;

  /** Maximum number of individual failures returned in a bulk response. */
  public static final int MAX_FAILURE_DETAILS = 100;

  /** The required JAR path argument. */
  public static final String JAR_PATH_ARGUMENT = "jarPath";

  /** The optional package prefix argument. */
  public static final String PACKAGE_PREFIX_ARGUMENT = "packagePrefix";

  /** The required binary class name argument. */
  public static final String CLASS_NAME_ARGUMENT = "className";

  /** The required logical JAR resource path argument. */
  public static final String RESOURCE_NAME_ARGUMENT = "resourceName";

  /** The optional prefix used to filter resource names. */
  public static final String RESOURCE_PREFIX_ARGUMENT = "resourcePrefix";

  /** The optional maximum resource result count. */
  public static final String MAX_RESOURCES_ARGUMENT = "maxResources";

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

  /** Default resource count returned by one list request. */
  public static final int DEFAULT_MAX_RESOURCES = 100;

  /** Maximum resource count returned by one list request. */
  public static final int MAX_RESOURCES_LIMIT = 1000;

  private McpToolConstants() {
    throw new AssertionError("No instances");
  }
}
