package cn.aslight.workhub.service.runtime;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.EnumSet;
import java.util.List;

/** 在写入认证配置之前建立和核对私有访问权限。 */
public final class PrivateFiles {
    private PrivateFiles() {}

    private static FileAttribute<?> permissions(Path path, boolean directory) throws IOException {
        if (!HostTools.windows()) return PosixFilePermissions.asFileAttribute(
                PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
        var lookup = path.getFileSystem().getUserPrincipalLookupService();
        var user = lookup.lookupPrincipalByName(System.getProperty("user.name"));
        var entry = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(user)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class));
        if (directory) entry.setFlags(AclEntryFlag.DIRECTORY_INHERIT, AclEntryFlag.FILE_INHERIT);
        List<AclEntry> entries = List.of(entry.build());
        return new FileAttribute<List<AclEntry>>() {
            public String name() { return "acl:acl"; }
            public List<AclEntry> value() { return entries; }
        };
    }

    public static Path tempDirectory(String prefix) throws IOException {
        Path temp = Path.of(System.getProperty("java.io.tmpdir"));
        return directory(Files.createTempDirectory(temp, prefix, permissions(temp, true)));
    }

    public static Path directory(Path path) throws IOException {
        var attr = permissions(path, true);
        Files.createDirectories(path, attr);
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("无效私有目录：" + path);
        if (!HostTools.windows()) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"));
        } else {
            var acl = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            if (acl == null) throw new IOException("文件系统不支持 Windows ACL：" + path);
            var owner = path.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name"));
            boolean grantsAccess = acl.getAcl().stream().anyMatch(e -> e.type() == AclEntryType.ALLOW
                    && !e.principal().equals(owner) && !e.principal().getName().equalsIgnoreCase("NT AUTHORITY\\SYSTEM")
                    && !e.principal().getName().equalsIgnoreCase("BUILTIN\\Administrators"));
            if (grantsAccess) throw new IOException("目录向其他账号授予了访问权限：" + path);
        }
        return path;
    }

    public static void writeNew(Path path, byte[] bytes) throws IOException {
        Files.createFile(path, permissions(path, false));
        Files.write(path, bytes, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
    }
}
