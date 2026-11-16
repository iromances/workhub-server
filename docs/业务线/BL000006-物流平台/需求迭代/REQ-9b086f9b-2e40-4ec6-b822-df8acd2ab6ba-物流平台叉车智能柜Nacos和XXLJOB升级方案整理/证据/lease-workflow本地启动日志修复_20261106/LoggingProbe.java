import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.core.env.StandardEnvironment;
public class LoggingProbe {
  public static void main(String[] args) {
    try {
      LoggingSystem system = LoggingSystem.get(Thread.currentThread().getContextClassLoader());
      system.beforeInitialize();
      system.initialize(new LoggingInitializationContext(new StandardEnvironment()), args[0], null);
      System.err.println("LOGGING_INITIALIZATION_OK");
      system.cleanUp();
    } catch (Throwable failure) { failure.printStackTrace(System.err); System.exit(1); }
  }
}
