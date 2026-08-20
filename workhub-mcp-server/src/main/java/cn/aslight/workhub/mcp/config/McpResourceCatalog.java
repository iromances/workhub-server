package cn.aslight.workhub.mcp.config;

import java.util.List;
import java.util.Map;

/**
 * Resource catalog used by the standalone MCP process.
 */
public record McpResourceCatalog(List<BusinessLine> businessLines,
                                 List<Environment> environments,
                                 List<DatabaseTarget> databaseTargets,
                                 List<ServerTarget> serverTargets,
                                 Map<String, Object> knowledge,
                                 Map<String, Object> gitlab) {

    public List<BusinessLine> businessLines() {
        return businessLines == null ? List.of() : businessLines;
    }

    public List<Environment> environments() {
        return environments == null ? List.of() : environments;
    }

    public List<DatabaseTarget> databaseTargets() {
        return databaseTargets == null ? List.of() : databaseTargets;
    }

    public List<ServerTarget> serverTargets() {
        return serverTargets == null ? List.of() : serverTargets;
    }

    public Map<String, Object> knowledge() {
        return knowledge == null ? Map.of() : knowledge;
    }

    public Map<String, Object> gitlab() {
        return gitlab == null ? Map.of() : gitlab;
    }

    public DatabaseTarget requireDatabaseTarget(String targetKey) {
        return databaseTargets().stream()
                .filter(target -> target.key().equals(targetKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("数据库目标不存在：" + targetKey));
    }

    public DatabaseTarget.DatabaseProfile requireDatabaseProfile(String targetKey, String profileKey) {
        DatabaseTarget target = requireDatabaseTarget(targetKey);
        return target.profiles().stream()
                .filter(profile -> profile.key().equals(profileKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("数据库权限 profile 不存在：" + targetKey + "." + profileKey));
    }

    public ServerTarget requireServerTarget(String targetKey) {
        return serverTargets().stream()
                .filter(target -> target.key().equals(targetKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("服务器目标不存在：" + targetKey));
    }

    public ServerTarget.ServerProfile requireServerProfile(String targetKey, String profileKey) {
        ServerTarget target = requireServerTarget(targetKey);
        return target.profiles().stream()
                .filter(profile -> profile.key().equals(profileKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("服务器权限 profile 不存在：" + targetKey + "." + profileKey));
    }

    public record BusinessLine(String code,
                               String name,
                               String gitlabGroupName,
                               List<String> involvedSystems,
                               List<String> globalSystems,
                               boolean enabled) {
        public List<String> involvedSystems() {
            return involvedSystems == null ? List.of() : involvedSystems;
        }

        public List<String> globalSystems() {
            return globalSystems == null ? List.of() : globalSystems;
        }
    }

    public record Environment(String code, String name, boolean production, boolean enabled) {
    }

    public record DatabaseTarget(String key,
                                 boolean publicResource,
                                 List<String> featureTags,
                                 String businessLineCode,
                                 List<String> businessLineCodes,
                                 String environmentCode,
                                 String name,
                                 String systemName,
                                 List<String> systemNames,
                                 String host,
                                 int port,
                                 String schema,
                                 String username,
                                 String password,
                                 SshTunnel sshTunnel,
                                 List<DatabaseProfile> profiles) {
        public DatabaseTarget(String key,
                              String businessLineCode,
                              List<String> businessLineCodes,
                              String environmentCode,
                              String name,
                              String systemName,
                              List<String> systemNames,
                              String host,
                              int port,
                              String schema,
                              String username,
                              String password,
                              SshTunnel sshTunnel,
                              List<DatabaseProfile> profiles) {
            this(key, false, List.of(), businessLineCode, businessLineCodes, environmentCode, name,
                    systemName, systemNames, host, port, schema, username, password, sshTunnel, profiles);
        }

        public List<DatabaseProfile> profiles() {
            return profiles == null ? List.of() : profiles;
        }

        public List<String> businessLineCodes() {
            if (businessLineCodes != null && !businessLineCodes.isEmpty()) {
                return businessLineCodes;
            }
            return businessLineCode == null || businessLineCode.isBlank() ? List.of() : List.of(businessLineCode);
        }

        public List<String> featureTags() {
            return featureTags == null ? List.of() : featureTags;
        }

        public List<String> systemNames() {
            if (systemNames != null && !systemNames.isEmpty()) {
                return systemNames;
            }
            return systemName == null || systemName.isBlank() ? List.of() : List.of(systemName);
        }

        public record SshTunnel(String bastionHost,
                                int bastionPort,
                                String bastionUser,
                                String password,
                                String identityFile) {
        }

        public record DatabaseProfile(String key,
                                      int maxRows,
                                      int queryTimeoutSeconds,
                                      int maxResultBytes) {
        }
    }

    public record ServerTarget(String key,
                               boolean publicResource,
                               List<String> featureTags,
                               String businessLineCode,
                               List<String> businessLineCodes,
                               String environmentCode,
                               String name,
                               String systemName,
                               List<String> systemNames,
                               String host,
                               int port,
                               String username,
                               String password,
                               String identityFile,
                               DatabaseTarget.SshTunnel sshTunnel,
                               List<String> allowedServices,
                               List<String> allowedLogPaths,
                               List<ServerProfile> profiles) {
        public ServerTarget(String key,
                            String businessLineCode,
                            List<String> businessLineCodes,
                            String environmentCode,
                            String name,
                            String systemName,
                            List<String> systemNames,
                            String host,
                            int port,
                            String username,
                            String password,
                            String identityFile,
                            DatabaseTarget.SshTunnel sshTunnel,
                            List<String> allowedServices,
                            List<String> allowedLogPaths,
                            List<ServerProfile> profiles) {
            this(key, false, List.of(), businessLineCode, businessLineCodes, environmentCode, name,
                    systemName, systemNames, host, port, username, password, identityFile, sshTunnel,
                    allowedServices, allowedLogPaths, profiles);
        }

        public List<String> allowedServices() {
            return allowedServices == null ? List.of() : allowedServices;
        }

        public List<String> businessLineCodes() {
            if (businessLineCodes != null && !businessLineCodes.isEmpty()) {
                return businessLineCodes;
            }
            return businessLineCode == null || businessLineCode.isBlank() ? List.of() : List.of(businessLineCode);
        }

        public List<String> featureTags() {
            return featureTags == null ? List.of() : featureTags;
        }

        public List<String> systemNames() {
            if (systemNames != null && !systemNames.isEmpty()) {
                return systemNames;
            }
            return systemName == null || systemName.isBlank() ? List.of() : List.of(systemName);
        }

        public List<String> allowedLogPaths() {
            return allowedLogPaths == null ? List.of() : allowedLogPaths;
        }

        public List<ServerProfile> profiles() {
            return profiles == null ? List.of() : profiles;
        }

        public record ServerProfile(String key,
                                    int maxOutputLines,
                                    int timeoutSeconds) {
        }
    }
}
