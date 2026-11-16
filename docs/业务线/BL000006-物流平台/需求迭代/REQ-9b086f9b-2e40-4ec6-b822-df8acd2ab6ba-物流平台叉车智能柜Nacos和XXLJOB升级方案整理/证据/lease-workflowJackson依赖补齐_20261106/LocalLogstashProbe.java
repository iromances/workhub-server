import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.slf4j.*;
import org.springframework.boot.logging.*;
import org.springframework.core.env.StandardEnvironment;

public class LocalLogstashProbe {
  public static void main(String[] args) throws Exception {
    String marker = "LEASE_WORKFLOW_JACKSON_ALIGNMENT_20261106";
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
      server.setSoTimeout(10000);
      String original = new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8);
      if (!original.contains("<appender-ref ref=\"LOGSTASH\"/>")) throw new AssertionError("LOGSTASH must remain enabled");
      String isolated = original.replaceAll("<remoteHost>[^<]*</remoteHost>", "<remoteHost>127.0.0.1</remoteHost>")
          .replaceAll("<port>[^<]*</port>", "<port>" + server.getLocalPort() + "</port>");
      Path config = Paths.get(args[1]);
      Files.write(config, isolated.getBytes(StandardCharsets.UTF_8));
      LoggingSystem system = LoggingSystem.get(Thread.currentThread().getContextClassLoader());
      try {
        system.beforeInitialize();
        system.initialize(new LoggingInitializationContext(new StandardEnvironment()), config.toString(), null);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        System.out.println("REGISTERED_JACKSON_MODULES=" + mapper.getRegisteredModuleIds());
        Class<?> jaxb = Class.forName("com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule");
        System.out.println("JAXB_PROVIDER=" + jaxb.getProtectionDomain().getCodeSource().getLocation());
        Logger logger = LoggerFactory.getLogger("lease.workflow.logstash.isolated.verification");
        logger.info(marker + " info");
        logger.error(marker + " error", new IllegalStateException("EXPECTED_LOGGING_PROBE_EXCEPTION"));
        int seen = 0;
        try (Socket client = server.accept(); BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))) {
          client.setSoTimeout(10000);
          for (int n = 0; n < 20 && seen < 2; n++) {
            String line = reader.readLine();
            if (line == null) break;
            JsonNode json = mapper.readTree(line);
            String message = json.path("message").asText();
            if (message.startsWith(marker)) {
              if (message.endsWith("error") && !json.path("stack_trace").asText().contains("EXPECTED_LOGGING_PROBE_EXCEPTION")) {
                throw new AssertionError("Exception stack must be encoded");
              }
              System.out.println("RECEIVED_JSON=" + line);
              seen++;
            }
          }
        }
        if (seen != 2) throw new AssertionError("Expected two JSON log events, got " + seen);
        System.out.println("LOGSTASH_LOOPBACK_OK profile=" + args[2] + " events=" + seen);
      } finally {
        ((ch.qos.logback.classic.LoggerContext)LoggerFactory.getILoggerFactory()).stop();
        system.cleanUp();
      }
    }
  }
}
