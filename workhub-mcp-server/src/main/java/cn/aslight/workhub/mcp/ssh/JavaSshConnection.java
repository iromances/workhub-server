package cn.aslight.workhub.mcp.ssh;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Windows 原生 SSH 会话，主机密钥由服务账号的 known_hosts 校验。 */
public final class JavaSshConnection implements SshConnectionManager.Connection {
    private final Session session;

    private JavaSshConnection(Session session) {
        this.session = session;
    }

    public static boolean windows() {
        return System.getProperty("os.name", "").startsWith("Windows");
    }

    static JavaSshConnection open(SshConnectionManager.Credentials credentials) throws IOException {
        var endpoint = credentials.endpoint;
        return new JavaSshConnection(connect(endpoint.host(), endpoint.port(), endpoint.username(),
                credentials.password, credentials.identityFile, null));
    }

    public static void probe(String host, int port, String user, String password, String identity) throws IOException {
        Session session = connect(host, port, user, password, identity, null);
        session.disconnect();
    }

    public static Session connect(String host, int port, String user, String password, String identity,
                                  String hostKeyAlias) throws IOException {
        var jsch = new JSch();
        Path ssh = Path.of(System.getProperty("user.home"), ".ssh");
        Path knownHosts = ssh.resolve("known_hosts");
        if (!Files.isRegularFile(knownHosts)) throw new IOException("请先为服务账号配置已核实的 SSH known_hosts");
        Session session = null;
        try {
            jsch.setKnownHosts(knownHosts.toString());
            if (password == null || password.isBlank()) {
                if (identity != null && !identity.isBlank()) jsch.addIdentity(identity);
                else {
                    for (String name : java.util.List.of("id_ed25519", "id_ecdsa", "id_rsa")) {
                        if (Files.isRegularFile(ssh.resolve(name))) jsch.addIdentity(ssh.resolve(name).toString());
                    }
                }
            }
            session = jsch.getSession(user, host, port);
            session.setConfig("StrictHostKeyChecking", "yes");
            if (hostKeyAlias != null) session.setHostKeyAlias(hostKeyAlias);
            if (password != null && !password.isBlank()) {
                session.setPassword(password);
                session.setConfig("PreferredAuthentications", "password,keyboard-interactive");
            }
            session.setServerAliveInterval(60000);
            session.setServerAliveCountMax(3);
            session.connect(8000);
            return session;
        } catch (JSchException ex) {
            if (session != null) session.disconnect();
            throw new IOException("SSH 连接失败，请核对服务账号的主机指纹、凭据和网络", ex);
        }
    }

    @Override public boolean healthy() { return session.isConnected(); }

    @Override public int forward(SshConnectionManager.Target target) throws IOException {
        try {
            return session.setPortForwardingL("127.0.0.1", 0, target.host(), target.port());
        } catch (JSchException ex) {
            throw new IOException("SSH 回环端口转发失败", ex);
        }
    }

    @Override public void close() { session.disconnect(); }
}
