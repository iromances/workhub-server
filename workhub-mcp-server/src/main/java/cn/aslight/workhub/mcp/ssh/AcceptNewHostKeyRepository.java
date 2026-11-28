package cn.aslight.workhub.mcp.ssh;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.UserInfo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/** 按 OpenSSH accept-new 语义校验主机公钥：未登记的公钥首次出现时追加到 known_hosts 并放行，其余结果原样返回。只追加，不改写已有内容。 */
final class AcceptNewHostKeyRepository implements HostKeyRepository {
    private static final System.Logger LOG = System.getLogger(AcceptNewHostKeyRepository.class.getName());

    private final HostKeyRepository delegate;
    private final Path knownHosts;

    AcceptNewHostKeyRepository(HostKeyRepository delegate, Path knownHosts) {
        this.delegate = delegate;
        this.knownHosts = knownHosts;
    }

    @Override
    public int check(String host, byte[] key) {
        int result = delegate.check(host, key);
        if (result != NOT_INCLUDED || key == null) return result;
        record(host, key);
        return OK;
    }

    private void record(String host, byte[] key) {
        try {
            HostKey hostKey = new HostKey(host, key);
            String entry = hostKey.getHost() + " " + hostKey.getType() + " " + hostKey.getKey() + "\n";
            if (lacksTrailingLineBreak()) entry = "\n" + entry;
            Files.createDirectories(knownHosts.getParent());
            Files.writeString(knownHosts, entry, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            // 首次信任不经人工核实，留下指纹供事后比对。
            LOG.log(System.Logger.Level.WARNING, "首次登记 SSH 主机公钥：" + hostKey.getHost() + " " + hostKey.getType()
                    + " SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(key)));
        } catch (IOException | JSchException | NoSuchAlgorithmException ex) {
            throw new UncheckedIOException(new IOException(ex));
        }
    }

    private boolean lacksTrailingLineBreak() throws IOException {
        if (!Files.isRegularFile(knownHosts) || Files.size(knownHosts) == 0) return false;
        ByteBuffer last = ByteBuffer.allocate(1);
        try (var channel = Files.newByteChannel(knownHosts)) {
            channel.position(channel.size() - 1);
            channel.read(last);
        }
        return last.get(0) != '\n' && last.get(0) != '\r';
    }

    @Override public void add(HostKey hostkey, UserInfo userinfo) { delegate.add(hostkey, userinfo); }
    @Override public void remove(String host, String type) { delegate.remove(host, type); }
    @Override public void remove(String host, String type, byte[] key) { delegate.remove(host, type, key); }
    @Override public String getKnownHostsRepositoryID() { return delegate.getKnownHostsRepositoryID(); }
    @Override public HostKey[] getHostKey(String host, String type) { return delegate.getHostKey(host, type); }
    @Override public HostKey[] getHostKey() { return delegate.getHostKey(); }
}
