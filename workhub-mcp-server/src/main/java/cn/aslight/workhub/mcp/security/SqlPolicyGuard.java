package cn.aslight.workhub.mcp.security;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Defensive read-only SQL policy. Database permissions must still be enforced by MySQL accounts.
 */
public class SqlPolicyGuard {

    private static final Pattern LIMIT_PATTERN = Pattern.compile("\\blimit\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DANGEROUS_PATTERN = Pattern.compile("""
            \\b(insert|update|delete|drop|alter|create|truncate|grant|revoke|replace|merge|call|load|outfile|infile|lock|unlock|set|use|handler)\\b
            """.strip(), Pattern.CASE_INSENSITIVE);

    public String validateReadonly(String rawSql, int maxRows) {
        if (maxRows <= 0) {
            throw new IllegalArgumentException("最大返回行数必须大于 0");
        }
        String sql = normalize(rawSql);
        rejectComments(sql);
        rejectMultipleStatements(sql);
        rejectDangerousKeywords(sql);
        String lower = sql.toLowerCase(Locale.ROOT);
        if (!isAllowedReadonlyPrefix(lower)) {
            throw new IllegalArgumentException("只允许只读 SQL：SELECT/WITH/SHOW/DESC/DESCRIBE/EXPLAIN");
        }
        if ((lower.startsWith("select ") || lower.startsWith("with ")) && !LIMIT_PATTERN.matcher(sql).find()) {
            return sql + " LIMIT " + maxRows;
        }
        return sql;
    }

    private String normalize(String rawSql) {
        if (rawSql == null) {
            throw new IllegalArgumentException("SQL 不能为空");
        }
        String sql = rawSql.trim();
        if (sql.isEmpty()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).trim();
        }
        return sql.replaceAll("\\s+", " ");
    }

    private void rejectComments(String sql) {
        if (sql.contains("--") || sql.contains("/*") || sql.contains("*/") || sql.contains("#")) {
            throw new IllegalArgumentException("SQL 不允许包含注释");
        }
    }

    private void rejectMultipleStatements(String sql) {
        if (sql.contains(";")) {
            throw new IllegalArgumentException("SQL 只允许单条语句");
        }
    }

    private void rejectDangerousKeywords(String sql) {
        if (DANGEROUS_PATTERN.matcher(sql).find()) {
            throw new IllegalArgumentException("SQL 包含非只读或高风险关键字");
        }
    }

    private boolean isAllowedReadonlyPrefix(String lowerSql) {
        return lowerSql.startsWith("select ")
                || lowerSql.startsWith("with ")
                || lowerSql.startsWith("show ")
                || lowerSql.startsWith("desc ")
                || lowerSql.startsWith("describe ")
                || lowerSql.startsWith("explain ");
    }
}
