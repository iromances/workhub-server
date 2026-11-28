package cn.aslight.workhub.mcp.ssh;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.UserInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AcceptNewHostKeyRepositoryTest {
    private static final String HOST = "[xxt-jumpserver.thchengtay.com]:62222";

    @TempDir
    Path home;

    @Test
    void unknownHostKeyIsAcceptedAndRecordedWhenAccountHasNoSshDirectory() throws Exception {
        byte[] key = key();
        Path knownHosts = home.resolve(".ssh/known_hosts");
        var repository = new AcceptNewHostKeyRepository(new UnknownOnly(), knownHosts);

        assertEquals(HostKeyRepository.OK, repository.check(HOST, key));
        assertEquals(entry(HOST, key), Files.readString(knownHosts));

        // 写出的条目要能被 JSch 自己的 KnownHosts 读回并校验。
        var reloaded = new JSch();
        reloaded.setKnownHosts(knownHosts.toString());
        assertEquals(HostKeyRepository.OK, reloaded.getHostKeyRepository().check(HOST, key));
        assertEquals(HostKeyRepository.CHANGED, reloaded.getHostKeyRepository().check(HOST, key()));
    }

    @Test
    void matchingHostKeyIsAcceptedWithoutChangingTheFile() throws Exception {
        byte[] key = key();
        Path knownHosts = home.resolve("known_hosts");
        Files.writeString(knownHosts, entry(HOST, key));
        byte[] before = Files.readAllBytes(knownHosts);
        var repository = new AcceptNewHostKeyRepository(knownHosts(knownHosts), knownHosts);

        assertEquals(HostKeyRepository.OK, repository.check(HOST, key));
        assertArrayEquals(before, Files.readAllBytes(knownHosts));
    }

    @Test
    void changedHostKeyIsRejectedAndFileStaysUntouched() throws Exception {
        Path knownHosts = home.resolve("known_hosts");
        Files.writeString(knownHosts, entry(HOST, key()));
        byte[] before = Files.readAllBytes(knownHosts);
        var repository = new AcceptNewHostKeyRepository(knownHosts(knownHosts), knownHosts);

        assertEquals(HostKeyRepository.CHANGED, repository.check(HOST, key()));
        assertArrayEquals(before, Files.readAllBytes(knownHosts));
    }

    @Test
    void missingTrailingLineBreakDoesNotGlueTheNewEntryToThePreviousLine() throws Exception {
        byte[] key = key();
        Path knownHosts = home.resolve("known_hosts");
        String existing = entry("[10.0.0.1]:22", key()).stripTrailing();
        Files.writeString(knownHosts, existing);
        var repository = new AcceptNewHostKeyRepository(knownHosts(knownHosts), knownHosts);

        assertEquals(HostKeyRepository.OK, repository.check(HOST, key));
        assertEquals(existing + "\n" + entry(HOST, key), Files.readString(knownHosts));
    }

    @Test
    void windowsLineEndingsAreKeptWithoutAnExtraBlankLine() throws Exception {
        byte[] key = key();
        Path knownHosts = home.resolve("known_hosts");
        String existing = entry("[10.0.0.1]:22", key()).replace("\n", "\r\n");
        Files.writeString(knownHosts, existing);
        var repository = new AcceptNewHostKeyRepository(knownHosts(knownHosts), knownHosts);

        assertEquals(HostKeyRepository.OK, repository.check(HOST, key));
        assertEquals(existing + entry(HOST, key), Files.readString(knownHosts));
    }

    @Test
    void unwritableKnownHostsFailsInsteadOfSilentlyContinuing() throws Exception {
        Path knownHosts = home.resolve("known_hosts");
        Files.createDirectory(knownHosts);
        var repository = new AcceptNewHostKeyRepository(new UnknownOnly(), knownHosts);

        assertThrows(UncheckedIOException.class, () -> repository.check(HOST, key()));
    }

    private static HostKeyRepository knownHosts(Path knownHosts) throws JSchException {
        JSch jsch = new JSch();
        jsch.setKnownHosts(knownHosts.toString());
        return jsch.getHostKeyRepository();
    }

    private static byte[] key() throws JSchException {
        KeyPair pair = KeyPair.genKeyPair(new JSch(), KeyPair.ECDSA, 256);
        try {
            return pair.getPublicKeyBlob();
        } finally {
            pair.dispose();
        }
    }

    private static String entry(String host, byte[] key) throws JSchException {
        HostKey hostKey = new HostKey(host, key);
        return hostKey.getHost() + " " + hostKey.getType() + " " + hostKey.getKey() + "\n";
    }

    /** 模拟文件尚不存在时 JSch 的仓库：任何公钥都未登记。 */
    private static final class UnknownOnly implements HostKeyRepository {
        @Override public int check(String host, byte[] key) { return NOT_INCLUDED; }
        @Override public void add(HostKey hostkey, UserInfo userinfo) { }
        @Override public void remove(String host, String type) { }
        @Override public void remove(String host, String type, byte[] key) { }
        @Override public String getKnownHostsRepositoryID() { return "unknown-only"; }
        @Override public HostKey[] getHostKey(String host, String type) { return new HostKey[0]; }
        @Override public HostKey[] getHostKey() { return new HostKey[0]; }
    }
}
