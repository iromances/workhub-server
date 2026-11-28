package cn.aslight.workhub.service.runtime;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

public final class HostTools {
    private HostTools() {}

    public static boolean windows() {
        return File.separatorChar == '\\';
    }

    public static boolean windowsEnvironment(String key) {
        return windows() && Set.of("PATH", "SYSTEMROOT", "WINDIR", "TEMP", "TMP", "USERPROFILE",
                "LOCALAPPDATA", "APPDATA").contains(key.toUpperCase(Locale.ROOT));
    }

    public static String git() {
        return executable(System.getenv().getOrDefault("WORKHUB_GIT_EXECUTABLE", "git"));
    }

    public static String executable(String command) {
        if (command == null || command.isBlank() || command.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("本机程序名无效");
        Path input = Path.of(command);
        if (input.isAbsolute()) return requireProgram(input);
        if (!command.matches("[a-zA-Z0-9_.-]+")) throw new IllegalArgumentException("请指定程序名或完整路径");
        String name = windows() && !command.toLowerCase(Locale.ROOT).endsWith(".exe") ? command + ".exe" : command;
        for (String entry : System.getenv().getOrDefault("PATH", "").split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (entry.isBlank()) continue;
            Path folder = Path.of(entry);
            if (!folder.isAbsolute()) continue;
            Path candidate = folder.resolve(name);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) return requireProgram(candidate);
        }
        throw new IllegalArgumentException("运行账号未找到已安装的程序：" + command);
    }

    private static String requireProgram(Path path) {
        if (!Files.isRegularFile(path) || !Files.isExecutable(path)
                || windows() && !path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".exe")) {
            throw new IllegalArgumentException("程序不存在或无法直接执行：" + path);
        }
        return path.normalize().toString();
    }
}
