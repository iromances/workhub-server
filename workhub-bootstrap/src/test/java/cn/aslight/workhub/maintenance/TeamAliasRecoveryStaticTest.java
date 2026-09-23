package cn.aslight.workhub.maintenance;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.internal.parser.ParsingContext;
import org.flywaydb.core.internal.resource.StringResource;
import org.flywaydb.database.mysql.MySQLDatabaseType;
import org.flywaydb.database.mysql.MySQLParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 无存储过程恢复方案的离线验证，不建立数据库连接。
 */
class TeamAliasRecoveryStaticTest {

    @Test
    void preparationContainsOnlyTableCreation() throws Exception {
        List<String> statements = parseMigration("V20260920_150742__merge_team_developer_aliases.sql");
        assertEquals(9, statements.size());
        assertTrue(statements.stream().allMatch(sql -> sql.startsWith("CREATE TABLE IF NOT EXISTS ")));
    }

    @Test
    void mergeContainsNoImplicitCommitOrRoutineStatements() throws Exception {
        List<String> statements = parseMigration("V20260920_155225__apply_team_developer_alias_merge.sql");
        assertTrue(statements.size() > 20);
        for (String sql : statements) {
            assertTrue(sql.matches("(?s)^(SET\\s+@|(?:INSERT|UPDATE|DELETE|WITH)\\s).*"), sql);
        }
    }

    @Test
    void mergeDoesNotDependOnRowCountAcrossFlywayStatements() throws Exception {
        for (String statement : parseMigration("V20260920_155225__apply_team_developer_alias_merge.sql")) {
            String executableSql = statement.replaceAll("(?m)--[^\\n]*", "");
            assertTrue(!executableSql.toUpperCase(Locale.ROOT).matches("(?s).*\\bROW_COUNT\\s*\\(.*"),
                    "Flyway读取SQL警告会改变ROW_COUNT，迁移校验必须查询实际数据");
        }
    }

    @Test
    void declaresDdlAndDmlTransactionBoundariesExplicitly() throws Exception {
        assertTransactionSetting("V20260920_150742__merge_team_developer_aliases.sql", "false");
        assertTransactionSetting("V20260920_155225__apply_team_developer_alias_merge.sql", "true");
    }

    @Test
    void restoreUsesNoRoutineOrImplicitCommit() throws Exception {
        String sql = Files.readString(Path.of("..", "docs", "WorkHub", "团队重复人员合并", "SDD", "rollback.sql"));
        for (String statement : parse(sql)) {
            assertTrue(statement.matches("(?s)^(SET|SELECT|INSERT|PREPARE|EXECUTE|DEALLOCATE)\\s.*"), statement);
        }
    }

    @Test
    void flywayMysqlExecutionTemplateRollsBackSqlFailure() throws Exception {
        List<String> calls = new ArrayList<>();
        Connection connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getAutoCommit")) {
                        return true;
                    }
                    calls.add(method.getName() + (args == null ? "" : ":" + args[0]));
                    return null;
                });

        var execution = new MySQLDatabaseType().createTransactionalExecutionTemplate(connection, true);
        assertThrows(RuntimeException.class, () -> execution.execute(() -> {
            throw new SQLException("simulated CHECK constraint failure");
        }));

        assertEquals(List.of("setAutoCommit:false", "rollback", "setAutoCommit:true"), calls);
    }

    private void assertTransactionSetting(String filename, String value) throws Exception {
        Properties properties = new Properties();
        try (var input = getClass().getResourceAsStream("/db/schema/mysql/" + filename + ".conf")) {
            properties.load(input);
        }
        assertEquals(value, properties.getProperty("executeInTransaction"));
    }

    private List<String> parseMigration(String filename) throws Exception {
        try (var input = getClass().getResourceAsStream("/db/schema/mysql/" + filename)) {
            return parse(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private List<String> parse(String sql) {
        var parser = new MySQLParser(new FluentConfiguration().placeholderReplacement(false), new ParsingContext());
        List<String> statements = new ArrayList<>();
        try (var iterator = parser.parse(new StringResource(sql))) {
            while (iterator.hasNext()) {
                statements.add(iterator.next().getSql().replaceFirst("\\A(?:\\s*--[^\\n]*(?:\\n|$))*\\s*", "").strip());
            }
        }
        return statements;
    }
}
