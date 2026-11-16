import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationListener;
public class BootstrapProbe {
  public static void main(String[] args) {
    try {
      Class<?> source = Class.forName("com.lease.workflow.LeaseWorkflowApplication");
      SpringApplication app = new SpringApplication(source);
      app.addListeners((ApplicationListener<org.springframework.context.ApplicationEvent>) event -> {
        if (event instanceof ApplicationFailedEvent) {
          System.err.println("DIAGNOSTIC_STARTUP_FAILURE");
          ((ApplicationFailedEvent) event).getException().printStackTrace(System.err);
        }
        if (event instanceof ApplicationPreparedEvent) {
          throw new IllegalStateException("DIAGNOSTIC_STOP_BEFORE_BUSINESS_CONTEXT_REFRESH");
        }
      });
      app.run(args);
    } catch (Throwable failure) { failure.printStackTrace(System.err); System.exit(1); }
  }
}
