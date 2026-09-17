package cn.aslight.workhub.mcp.ssh;

import cn.aslight.workhub.mcp.config.McpResourceCatalog.DatabaseTarget;

import java.io.IOException;

/** 目标转发租约；关闭本对象不会关闭共享堡垒机连接。 */
public final class SshTunnel implements AutoCloseable {
    private final String host;
    private final int port;
    private final SshConnectionManager.Lease lease;

    private SshTunnel(String host, int port, SshConnectionManager.Lease lease) {
        this.host = host;
        this.port = port;
        this.lease = lease;
    }

    public static SshTunnel open(DatabaseTarget target) throws IOException {
        return open(target.sshTunnel(), target.host(), target.port());
    }

    public static SshTunnel open(DatabaseTarget.SshTunnel bastion, String host, int port) throws IOException {
        if (bastion == null) {
            return new SshTunnel(host, port, null);
        }
        SshConnectionManager.Lease lease = SshConnectionManager.shared().acquire(bastion, host, port);
        return new SshTunnel("127.0.0.1", lease.port(), lease);
    }

    public String host() { return host; }
    public int port() { return port; }

    @Override
    public void close() {
        if (lease != null) {
            lease.close();
        }
    }
}
