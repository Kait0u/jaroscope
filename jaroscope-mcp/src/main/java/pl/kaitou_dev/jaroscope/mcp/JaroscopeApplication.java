package pl.kaitou_dev.jaroscope.mcp;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import pl.kaitou_dev.jaroscope.cache.CacheStore;
import pl.kaitou_dev.jaroscope.config.ConfigurationLoader;
import pl.kaitou_dev.jaroscope.config.JaroscopeConfiguration;
import pl.kaitou_dev.jaroscope.decompiler.BackgroundDecompilationCoordinator;
import pl.kaitou_dev.jaroscope.decompiler.CachingDecompiler;
import pl.kaitou_dev.jaroscope.decompiler.VineflowerDecompiler;

/** Starts the local JARoscope MCP server over standard input and output. */
@Slf4j
public final class JaroscopeApplication {
  /** Server identity advertised during MCP initialization. */
  private static final String SERVER_NAME = "JARoscope";

  /** Server version advertised during MCP initialization. */
  private static final String SERVER_VERSION = "0.1.0-SNAPSHOT";

  /** Command-line option selecting an explicit configuration file. */
  private static final String CONFIG_ARGUMENT = "--config";

  private JaroscopeApplication() {
    throw new AssertionError("No instances");
  }

  /** Runs the already-configured stdio server. */
  public static void run(String[] arguments) throws Exception {
    Optional<Path> explicitConfiguration = explicitConfiguration(arguments);
    JaroscopeConfiguration configuration =
        new ConfigurationLoader()
            .load(Path.of(System.getProperty("user.home")), explicitConfiguration);
    StdioServerTransportProvider transport =
        new StdioServerTransportProvider(McpJsonDefaults.getMapper());
    CachingDecompiler decompiler =
        new CachingDecompiler(
            new VineflowerDecompiler(
                configuration.archiveLimits(),
                configuration.vineflowerThreadsPerRun(),
                new Semaphore(configuration.maxConcurrentVineflowerRuns(), true)),
            new CacheStore(configuration));
    BackgroundDecompilationCoordinator backgroundDecompilation =
        new BackgroundDecompilationCoordinator(configuration, decompiler);
    ListClassesTool listClassesTool = new ListClassesTool(configuration);
    GetClassInterfaceTool getClassInterfaceTool = new GetClassInterfaceTool(configuration);
    GetClassSourceTool getClassSourceTool =
        new GetClassSourceTool(configuration, backgroundDecompilation, decompiler);
    CacheStatusTool cacheStatusTool = new CacheStatusTool(configuration);
    CleanCacheTool cleanCacheTool = new CleanCacheTool(configuration);
    DecompileJarTool decompileJarTool = new DecompileJarTool(configuration, decompiler);
    McpSyncServer server =
        McpServer.sync(transport)
            .serverInfo(SERVER_NAME, SERVER_VERSION)
            .capabilities(ServerCapabilities.builder().tools(true).build())
            .tools(
                listClassesTool.specification(),
                getClassInterfaceTool.specification(),
                getClassSourceTool.specification(),
                cacheStatusTool.specification(),
                cleanCacheTool.specification(),
                decompileJarTool.specification())
            .build();
    CountDownLatch shutdown = new CountDownLatch(1);
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> close(server, backgroundDecompilation, shutdown), "jaroscope-shutdown"));
    log.info("JARoscope MCP server started");
    shutdown.await();
  }

  /** Finds an optional explicit configuration path in command-line arguments. */
  private static Optional<Path> explicitConfiguration(String[] arguments) {
    for (int index = 0; index < arguments.length - 1; ++index) {
      if (CONFIG_ARGUMENT.equals(arguments[index])) {
        return Optional.of(Path.of(arguments[index + 1]));
      }
    }
    return Optional.empty();
  }

  /** Closes the MCP server and releases the process wait latch. */
  private static void close(
      McpSyncServer server,
      BackgroundDecompilationCoordinator backgroundDecompilation,
      CountDownLatch shutdown) {
    server.close();
    backgroundDecompilation.close();
    shutdown.countDown();
  }
}
