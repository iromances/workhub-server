package cn.aslight.workhub.mcp.ssh;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SshTunnelTest {
    @Test
    void directConnectionPreservesAddressAndCloseIsHarmless() throws Exception {
        try (var tunnel = SshTunnel.open(null, "database", 3306)) {
            assertEquals("database", tunnel.host());
            assertEquals(3306, tunnel.port());
            tunnel.close();
        }
    }
}
