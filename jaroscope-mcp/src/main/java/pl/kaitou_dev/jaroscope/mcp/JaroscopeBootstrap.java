package pl.kaitou_dev.jaroscope.mcp;

import pl.kaitou_dev.jaroscope.mcp.logging.LoggingConfiguration;
import pl.kaitou_dev.jaroscope.mcp.logging.LoggingConfigurator;

/** Configures logging before loading the application class and starts JARoscope. */
public final class JaroscopeBootstrap {
  /** Prevents instances of this entrypoint type. */
  private JaroscopeBootstrap() {
    throw new AssertionError("No instances");
  }

  /** Configures stderr logging and starts the JARoscope application. */
  public static void main(String[] arguments) throws Exception {
    LoggingConfigurator.configure(LoggingConfiguration.defaults());
    JaroscopeApplication.run(arguments);
  }
}
