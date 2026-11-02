package cn.aslight.workhub.mcp.db;

import cn.aslight.workhub.mcp.audit.McpAuditLogger;
import cn.aslight.workhub.mcp.config.McpResourceCatalog;
import cn.aslight.workhub.mcp.config.McpResourceCatalog.DatabaseTarget;
import cn.aslight.workhub.mcp.config.McpResourceCatalog.DatabaseTarget.DatabaseProfile;
import cn.aslight.workhub.mcp.security.SecretResolver;
import cn.aslight.workhub.mcp.security.SensitiveDataMasker;
import cn.aslight.workhub.mcp.security.SqlPolicyGuard;
import cn.aslight.workhub.mcp.ssh.SshTunnel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DatabaseDiagnosticService {

    private final McpResourceCatalog catalog;
    private final SqlPolicyGuard sqlPolicyGuard;
    private final SecretResolver secretResolver;
    private final McpAuditLogger auditLogger;
    private final SensitiveDataMasker sensitiveDataMasker;

    public DatabaseDiagnosticService(McpResourceCatalog catalog,
                                     SqlPolicyGuard sqlPolicyGuard,
                                     SecretResolver secretResolver,
                                     McpAuditLogger auditLogger) {
        this.catalog = catalog;
        this.sqlPolicyGuard = sqlPolicyGuard;
        this.secretResolver = secretResolver;
        this.auditLogger = auditLogger;
        this.sensitiveDataMasker = new SensitiveDataMasker();
    }

    public Map<String, Object> describeDatabase(String targetKey, String profileKey) {
        DatabaseTarget target = catalog.requireDatabaseTarget(targetKey);
        DatabaseProfile profile = catalog.requireDatabaseProfile(targetKey, profileKey);
        Instant startedAt = Instant.now();
        try (SshTunnel tunnel = SshTunnel.open(target);
             JdbcConnectionLeaseCache.Lease lease = connection(target, tunnel)) {
            DatabaseMetaData metaData = lease.connection().getMetaData();
            List<Map<String, Object>> tables = new ArrayList<>();
            try (ResultSet tableRs = metaData.getTables(target.schema(), null, "%", new String[]{"TABLE", "VIEW"})) {
                int tableCount = 0;
                while (tableRs.next() && tableCount < Math.min(profile.maxRows(), 200)) {
                    String tableName = tableRs.getString("TABLE_NAME");
                    tables.add(Map.of(
                            "tableName", tableName,
                            "tableType", tableRs.getString("TABLE_TYPE"),
                            "columns", columns(metaData, target.schema(), tableName)
                    ));
                    tableCount++;
                }
            }
            audit("describe_database", target, profile, "SUCCEEDED", null, tables.size(), startedAt, null);
            return Map.of("targetKey", targetKey, "schema", target.schema(), "tables", tables);
        } catch (Exception ex) {
            audit("describe_database", target, profile, "FAILED", null, 0, startedAt, ex);
            throw new IllegalArgumentException("读取数据库结构失败：" + ex.getMessage(), ex);
        }
    }

    public Map<String, Object> explainQuery(String targetKey, String profileKey, String rawSql) {
        String sql = sqlPolicyGuard.validateReadonly(rawSql, catalog.requireDatabaseProfile(targetKey, profileKey).maxRows());
        if (!sql.toLowerCase(java.util.Locale.ROOT).startsWith("explain ")) {
            sql = "EXPLAIN " + sql;
        }
        return runQuery("explain_query", targetKey, profileKey, sql);
    }

    public Map<String, Object> runReadonlyQuery(String targetKey, String profileKey, String rawSql) {
        String sql = sqlPolicyGuard.validateReadonly(rawSql, catalog.requireDatabaseProfile(targetKey, profileKey).maxRows());
        return runQuery("run_readonly_query", targetKey, profileKey, sql);
    }

    /** 多条只读 SQL 复用同一个连接执行，返回与入参顺序一致的结果集。 */
    public List<Map<String, Object>> runReadonlyQueries(String targetKey, String profileKey, List<String> rawSqls) {
        DatabaseProfile profile = catalog.requireDatabaseProfile(targetKey, profileKey);
        List<String> sqls = rawSqls.stream()
                .map(rawSql -> sqlPolicyGuard.validateReadonly(rawSql, profile.maxRows()))
                .toList();
        return runStatements("run_readonly_query", targetKey, profileKey, sqls);
    }

    private Map<String, Object> runQuery(String toolName, String targetKey, String profileKey, String sql) {
        Map<String, Object> result = runStatements(toolName, targetKey, profileKey, List.of(sql)).getFirst();
        return Map.of(
                "targetKey", targetKey,
                "profileKey", profileKey,
                "sql", truncate(sensitiveDataMasker.maskSql(sql), 1000),
                "dataMasked", true,
                "result", result
        );
    }

    private List<Map<String, Object>> runStatements(String toolName, String targetKey, String profileKey, List<String> sqls) {
        DatabaseTarget target = catalog.requireDatabaseTarget(targetKey);
        DatabaseProfile profile = catalog.requireDatabaseProfile(targetKey, profileKey);
        Instant startedAt = Instant.now();
        try (SshTunnel tunnel = SshTunnel.open(target);
             JdbcConnectionLeaseCache.Lease lease = connection(target, tunnel)) {
            List<Map<String, Object>> results = new ArrayList<>();
            for (String sql : sqls) {
                Instant statementStartedAt = Instant.now();
                try (Statement statement = lease.connection().createStatement()) {
                    statement.setMaxRows(profile.maxRows());
                    statement.setQueryTimeout(profile.queryTimeoutSeconds());
                    try (ResultSet resultSet = statement.executeQuery(sql)) {
                        Map<String, Object> result = resultSetToRows(resultSet, profile.maxRows(), profile.maxResultBytes());
                        int rowCount = ((List<?>) result.get("rows")).size();
                        audit(toolName, target, profile, "SUCCEEDED", sql, rowCount, statementStartedAt, null);
                        results.add(result);
                    }
                } catch (Exception ex) {
                    lease.discard();
                    audit(toolName, target, profile, "FAILED", sql, 0, statementStartedAt, ex);
                    throw new IllegalArgumentException("数据库查询失败：" + safeErrorMessage(ex), ex);
                }
            }
            return results;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            audit(toolName, target, profile, "FAILED", sqls.getFirst(), 0, startedAt, ex);
            throw new IllegalArgumentException("数据库查询失败：" + safeErrorMessage(ex), ex);
        }
    }

    private JdbcConnectionLeaseCache.Lease connection(DatabaseTarget target, SshTunnel tunnel) throws SQLException {
        String password = secretResolver.resolve(target.password());
        return JdbcConnectionLeaseCache.shared()
                .acquire(jdbcUrl(tunnel.host(), tunnel.port(), target.schema()), target.username(), password);
    }

    static String jdbcUrl(String host, int port, String schema) {
        String databasePath = schema == null || schema.isBlank() ? "/" : "/" + schema;
        return "jdbc:mysql://" + host + ":" + port + databasePath
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&allowPublicKeyRetrieval=true&useSSL=false&connectTimeout=10000&socketTimeout=60000";
    }

    private List<Map<String, Object>> columns(DatabaseMetaData metaData, String schema, String tableName) throws Exception {
        List<Map<String, Object>> columns = new ArrayList<>();
        try (ResultSet columnRs = metaData.getColumns(schema, null, tableName, "%")) {
            while (columnRs.next()) {
                columns.add(Map.of(
                        "columnName", columnRs.getString("COLUMN_NAME"),
                        "typeName", columnRs.getString("TYPE_NAME"),
                        "nullable", columnRs.getInt("NULLABLE") == DatabaseMetaData.columnNullable,
                        "remarks", columnRs.getString("REMARKS") == null ? "" : columnRs.getString("REMARKS")
                ));
            }
        }
        return columns;
    }

    Map<String, Object> resultSetToRows(ResultSet resultSet, int maxRows, int maxBytes) throws Exception {
        ResultSetMetaData metaData = resultSet.getMetaData();
        List<String> columns = new ArrayList<>();
        List<String> sourceColumns = new ArrayList<>();
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            columns.add(metaData.getColumnLabel(i));
            sourceColumns.add(metaData.getColumnName(i));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        int byteBudget = maxBytes <= 0 ? 65536 : maxBytes;
        int currentBytes = 0;
        while (resultSet.next() && rows.size() < maxRows) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columns.size(); i++) {
                Object value = resultSet.getObject(i);
                row.put(columns.get(i - 1), sensitiveDataMasker.mask(columns.get(i - 1), sourceColumns.get(i - 1), value));
            }
            currentBytes += row.toString().getBytes(StandardCharsets.UTF_8).length;
            if (currentBytes > byteBudget) {
                break;
            }
            rows.add(row);
        }
        return Map.of(
                "columns", columns,
                "rows", rows,
                "truncatedByBytes", currentBytes > byteBudget,
                "dataMasked", true
        );
    }

    private void audit(String toolName,
                       DatabaseTarget target,
                       DatabaseProfile profile,
                       String status,
                       String sql,
                       int rows,
                       Instant startedAt,
                       Exception error) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("tool", toolName);
        fields.put("resourceType", "DATABASE");
        fields.put("businessLineCode", target.businessLineCode());
        fields.put("businessLineCodes", target.businessLineCodes());
        fields.put("environmentCode", target.environmentCode());
        fields.put("targetKey", target.key());
        fields.put("profileKey", profile.key());
        fields.put("sqlFingerprint", sql == null ? "" : fingerprint(sql));
        fields.put("sql", sql == null ? "" : truncate(sensitiveDataMasker.maskSql(sql), 1000));
        fields.put("status", status);
        fields.put("rowCount", rows);
        fields.put("durationMillis", Duration.between(startedAt, Instant.now()).toMillis());
        fields.put("error", error == null ? "" : truncate(safeErrorMessage(error), 500));
        auditLogger.record(fields);
    }

    private String safeErrorMessage(Exception error) {
        if (error == null || error.getMessage() == null) {
            return "数据库执行异常";
        }
        return sensitiveDataMasker.maskSql(sensitiveDataMasker.maskText(error.getMessage()));
    }

    private String fingerprint(String sql) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(sql.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return "";
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
