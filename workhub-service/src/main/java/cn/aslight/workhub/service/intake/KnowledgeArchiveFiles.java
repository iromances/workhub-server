package cn.aslight.workhub.service.intake;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/** 知识库文件写入：跨进程串行化、旧版本留存与原子替换。 */
final class KnowledgeArchiveFiles {
    private static final Object[] LOCKS = new Object[64];
    static { Arrays.setAll(LOCKS, ignored -> new Object()); }

    private KnowledgeArchiveFiles() { }

    static <T> T locked(Path vault, Operation<T> operation) throws IOException {
        synchronized (LOCKS[Math.floorMod(vault.hashCode(), LOCKS.length)]) {
            directory(vault, vault);
            Path lockPath = checked(vault, vault.resolve(".workhub-archive.lock"));
            try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                 FileLock ignored = channel.lock()) {
                return operation.run();
            }
        }
    }

    static Path checked(Path vault, Path target) throws IOException {
        Path root = vault.toAbsolutePath().normalize();
        Path normalized = target.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) {
            throw new IOException("归档路径超出知识库");
        }
        Path current = root;
        if (Files.isSymbolicLink(current)) {
            throw new IOException("知识库路径不能是符号链接");
        }
        for (Path segment : root.relativize(normalized)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("归档路径不能包含符号链接：" + current);
            }
        }
        return normalized;
    }

    static void directory(Path vault, Path path) throws IOException {
        Files.createDirectories(checked(vault, path));
    }

    static void versionedWrite(Path vault, Path target, byte[] content) throws IOException {
        target = checked(vault, target);
        directory(vault, target.getParent());
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("归档目标不是普通文件：" + target);
            }
            byte[] previous = Files.readAllBytes(target);
            if (MessageDigest.isEqual(previous, content)) {
                return;
            }
            Path historical = target.getParent().resolve("历史版本").resolve(digest(previous)).resolve(target.getFileName());
            immutableWrite(vault, historical, previous);
        }
        atomicWrite(vault, target, content);
    }

    static void immutableWrite(Path vault, Path target, byte[] content) throws IOException {
        checked(vault, target);
        directory(vault, target.getParent());
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                    || !MessageDigest.isEqual(Files.readAllBytes(target), content)) {
                throw new IOException("历史版本内容冲突：" + target);
            }
            return;
        }
        Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
    }

    static void atomicWrite(Path vault, Path target, byte[] content) throws IOException {
        checked(vault, target);
        directory(vault, target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".workhub-archive-", ".tmp");
        try {
            Files.write(temporary, content);
            checked(vault, target);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM 不支持 SHA-256", ex);
        }
    }

    @FunctionalInterface
    interface Operation<T> { T run() throws IOException; }
}
