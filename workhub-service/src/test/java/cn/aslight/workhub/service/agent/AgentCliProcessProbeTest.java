package cn.aslight.workhub.service.agent;

import org.junit.jupiter.api.Test;
import java.net.InetAddress;
import static org.junit.jupiter.api.Assertions.*;

class AgentCliProcessProbeTest {
    @Test
    void localPidAndStartTimeAreCheckedWithoutMistakingMissingParentForStoppedTree() throws Exception {
        var probe = new AgentCliProcessProbe();
        var current = ProcessHandle.current();
        String host = InetAddress.getLocalHost().getHostName();
        assertEquals("RUNNING", probe.observe(host, current.pid(), current.info().startInstant().orElseThrow().toString()));
        assertEquals("PID_REUSED", probe.observe(host, current.pid(), "2000-01-01T00:00:00Z"));
        assertEquals("LIVE_IDENTITY_UNAVAILABLE", probe.observe(host, current.pid(), null));
        assertEquals("LIVE_IDENTITY_UNAVAILABLE", probe.observe(host, current.pid(), "invalid-start"));
        assertEquals("PARENT_NOT_FOUND", probe.observe(host, Long.MAX_VALUE, "2000-01-01T00:00:00Z"));
        assertEquals("IDENTITY_UNAVAILABLE", probe.observe(host, null, null));
        assertEquals("REMOTE_HOST", probe.observe("remote-fixture.invalid", current.pid(), null));
    }
}
