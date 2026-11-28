package cn.aslight.workhub.mcp.runtime;

import java.nio.file.Files;
import java.nio.file.Path;

/** 独立 MCP 和 Boot 运行时共用宿主目录与 GitLab 命名空间规则。 */
public final class WorkhubPaths {
    private WorkhubPaths() {}

    public static final String PLAYBOOK_PROJECT = "engineering/agents-playbook";

    /** MCP 目录来自资源清单，旧版清单缺少协作仓库时沿用当前实例的默认仓库。 */
    public static Path businessLineArchive(java.util.Map<String, Object> catalog, String code, String name) {
        String segment = code == null || code.isBlank() || code.equals(name) ? name : code + "-" + name;
        if (segment == null || segment.isBlank() || segment.equals(".") || segment.equals("..")
                || segment.contains("/") || segment.contains("\\")) throw new IllegalArgumentException("业务线目录名称无效");
        Object configured = catalog.get("BL000012".equals(code) ? "projectDocumentPath" : "projectPlaybookPath");
        if (configured != null) {
            Path root = Path.of(configured.toString());
            return ("BL000012".equals(code) ? root.resolve("业务线") : root).resolve(segment);
        }
        if ("BL000012".equals(code) || catalog.containsKey("projectPlaybookPathError")) return null;
        return namespace(repositories(), PLAYBOOK_PROJECT).resolve("docs/业务线").resolve(segment);
    }

    public static Path repositories() {
        return configured("WORKHUB_REPOSITORIES_ROOT", "cache/repos");
    }

    public static Path standards() {
        return configured("WORKHUB_STANDARDS_ROOT", "config/standards");
    }

    private static Path configured(String key, String fallback) {
        String value = System.getenv(key);
        if (value != null && !value.isBlank()) {
            Path path = Path.of(value);
            if (!path.isAbsolute()) throw new IllegalArgumentException(key + " 必须是绝对路径");
            return path.normalize();
        }
        return applicationHome().resolve(fallback);
    }

    public static Path applicationHome() {
        // Boot 加载器的 CodeSource 位于外层可执行 JAR，业务类来自嵌套依赖。
        try {
            Class<?> launcher = Class.forName("org.springframework.boot.loader.launch.JarLauncher", false,
                    ClassLoader.getSystemClassLoader());
            Path jar = Path.of(launcher.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(jar)) return jar.toAbsolutePath().getParent();
        } catch (ClassNotFoundException ignored) {
            // IDE 以及独立 MCP 默认从工作目录定位工程。
        } catch (java.net.URISyntaxException ex) {
            throw new IllegalStateException("无法定位 WorkHub JAR", ex);
        }
        Path cwd = Path.of("").toAbsolutePath().normalize();
        for (Path dir = cwd; dir != null; dir = dir.getParent()) {
            if (Files.isDirectory(dir.resolve("workhub-bootstrap")) && Files.isRegularFile(dir.resolve("pom.xml"))) return dir;
        }
        return cwd;
    }

    public static Path namespace(Path root, String namespace) {
        if (namespace == null || namespace.isBlank()) throw new IllegalArgumentException("GitLab 项目路径为空");
        Path result = root;
        for (String segment : namespace.split("/", -1)) {
            if (!segment.matches("[\\p{L}\\p{N}_.-]+") || segment.equals(".") || segment.equals("..")
                    || segment.equalsIgnoreCase(".git") || segment.toLowerCase(java.util.Locale.ROOT).startsWith(".workhub-") || segment.endsWith(".")
                    || segment.matches("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(\\..*)?"))
                throw new IllegalArgumentException("GitLab 项目路径包含非法目录段");
            result = result.resolve(segment);
            if (Files.isSymbolicLink(result)) throw new IllegalArgumentException("GitLab 项目路径经过符号链接");
        }
        return result;
    }
}
