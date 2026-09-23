package cn.aslight.workhub.maintenance;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.internal.parser.ParsingContext;
import org.flywaydb.core.internal.resource.StringResource;
import org.flywaydb.database.mysql.MySQLDatabaseType;
import org.flywaydb.database.mysql.MySQLParser;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntakeClarificationRollbackMigrationTest {
    private static final String MIGRATION = "V20260921_230911__return_intake_202609180002_to_clarification.sql";

    @Test
    void mysqlParserAcceptsMigrationWithoutImplicitCommits() throws Exception {
        List<String> statements = statements();
        assertTrue(statements.size() > 20);
        for (String sql : statements) {
            assertTrue(sql.matches("(?s)^(SET\\s+@|SELECT\\s|UPDATE\\s|INSERT\\s|PREPARE\\s|EXECUTE\\s|DEALLOCATE\\s).*"), sql);
        }
        Properties config = new Properties();
        try (var input = getClass().getResourceAsStream("/db/schema/mysql/" + MIGRATION + ".conf")) {
            config.load(input);
        }
        assertEquals("true", config.getProperty("executeInTransaction"));
    }

    @Test
    void persistentWritesOnlyChangeOneIntakeAndAppendItsAudit() throws Exception {
        List<String> writes = statements().stream()
                .filter(sql -> sql.startsWith("UPDATE ") || sql.startsWith("INSERT ")).toList();
        assertEquals(2, writes.size());
        String update = writes.getFirst();
        assertTrue(update.startsWith("UPDATE pm_intake_record\nSET demand_status = '待澄清', updated_at = @wh_rb_at\nWHERE "));
        assertTrue(update.contains("id = 217 AND approval_code = '202609180002' AND business_line_code = 'BL000009'"));
        assertTrue(update.contains("demand_status = '待评估' AND updated_at = '2026-09-21 16:09:13'"));
        assertTrue(update.contains("AND NOT @wh_rb_done"));
        assertTrue(writes.getLast().startsWith("INSERT INTO pm_intake_history "));
        assertTrue(writes.getLast().contains("SELECT 217, 'UPDATE'"));
        assertTrue(writes.getLast().contains("WHERE NOT @wh_rb_done"));
    }

    @Test
    void flywayRollsBackOnAssertionFailureAndCommitsOnlyOnSuccess() throws Exception {
        assertEquals(List.of("setAutoCommit:false", "rollback", "setAutoCommit:true"), transactionCalls(true));
        assertEquals(List.of("setAutoCommit:false", "commit", "setAutoCommit:true"), transactionCalls(false));
    }

    private List<String> transactionCalls(boolean fail) throws Exception {
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
        if (fail) {
            assertThrows(RuntimeException.class, () -> execution.execute(() -> {
                throw new SQLException("unknown assertion column");
            }));
        } else {
            execution.execute(() -> null);
        }
        return calls;
    }

    private List<String> statements() throws Exception {
        String sql;
        try (var input = getClass().getResourceAsStream("/db/schema/mysql/" + MIGRATION)) {
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
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
