package cn.aslight.workhub.service.agent;

import org.springframework.stereotype.Component;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/** 只排除仍存活的同一进程；父 PID 消失不能证明其子进程树已停止。 */
@Component
public class AgentCliProcessProbe {
    public String observe(String host, Long pid, String startInstant) {
        try {
            if (!InetAddress.getLocalHost().getHostName().equals(host)) return "REMOTE_HOST";
            if (pid == null) return "IDENTITY_UNAVAILABLE";
            var process = ProcessHandle.of(pid).filter(ProcessHandle::isAlive);
            if (process.isEmpty()) return "PARENT_NOT_FOUND";
            var actualStart = process.get().info().startInstant();
            if (startInstant == null || actualStart.isEmpty()) return "LIVE_IDENTITY_UNAVAILABLE";
            return Instant.parse(startInstant).equals(actualStart.get()) ? "RUNNING" : "PID_REUSED";
        } catch (DateTimeParseException ex) {
            return "LIVE_IDENTITY_UNAVAILABLE";
        } catch (UnknownHostException | SecurityException ex) {
            return "IDENTITY_UNAVAILABLE";
        }
    }
}
