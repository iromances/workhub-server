package cn.aslight.workhub.service.ops;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-7f26a063：规则组迁移只建两张表并替换数据指南已知片段；H2（MODE=MySQL）重复执行不报错、不叠加。 */
class AccountingRuleMigrationTest {

    private static final String OLD_FRAGMENT =
            "ACCOUNTING_INTEGRITY 的规则来自内置目录及 func_call_sql 的 AI_* 模板，不使用旧 ops_account_rule_config；该类型仅手动或明确任务执行，不能按 check_time 推断漏调度";
    private static final String NEW_FRAGMENT =
            "ACCOUNTING_INTEGRITY 的规则来自配置下的规则组与检查项 ops_account_monitor_family / ops_account_monitor_check";

    private static List<String> statements() throws Exception {
        String sql = Files.readString(Path.of(
                "../workhub-bootstrap/src/main/resources/db/schema/mysql/V20261127_120000__accounting_rule_management.sql"));
        // 语句以行尾分号结束；字符串字面量内不使用 ASCII 分号。
        return Arrays.stream(sql.split(";\\s*\\n")).map(String::strip).filter(item -> !item.isEmpty()).toList();
    }

    @Test
    void createsBothTablesRepeatedlyWithoutTouchingData() throws Exception {
        List<String> statements = statements();
        assertEquals(3, statements.size());
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
             var statement = connection.createStatement()) {
            statement.execute(statements.get(0));
            statement.execute(statements.get(1));
            statement.execute("INSERT INTO ops_account_monitor_family(config_id,family_code,family_name,batch_size,source_type,created_by,updated_by)"
                    + " VALUES (1,'ol_demo','演示',10000,'BUILTIN','system','system')");
            statement.execute("INSERT INTO ops_account_monitor_check(family_id,check_id,check_name,severity,created_by,updated_by)"
                    + " VALUES (1,'TOTAL','总额','ERROR','system','system')");
            statement.execute("INSERT INTO ops_account_monitor_check(family_id,check_id,check_name,severity,enabled,created_by,updated_by)"
                    + " VALUES (1,'MISSING','缺失','WARN',0,'system','system')");
            statement.execute(statements.get(0));
            statement.execute(statements.get(1));
            try (var rows = statement.executeQuery("SELECT enabled, deleted, sort_order, created_by FROM ops_account_monitor_family")) {
                assertTrue(rows.next());
                assertEquals(true, rows.getBoolean(1));
                assertEquals(false, rows.getBoolean(2));
                assertEquals(0, rows.getInt(3));
                assertEquals("system", rows.getString(4));
                assertFalse(rows.next());
            }
            try (var rows = statement.executeQuery("SELECT check_id, enabled FROM ops_account_monitor_check ORDER BY id")) {
                List<String> checks = new ArrayList<>();
                while (rows.next()) checks.add(rows.getString(1) + "=" + rows.getBoolean(2));
                assertEquals(List.of("TOTAL=true", "MISSING=false"), checks);
            }
            assertRejected(statement, "INSERT INTO ops_account_monitor_family(config_id,family_code,family_name,batch_size,source_type,created_by,updated_by)"
                    + " VALUES (1,'ol_demo','重复',1,'CUSTOM','a','a')");
            assertRejected(statement, "INSERT INTO ops_account_monitor_check(family_id,check_id,check_name,severity,created_by,updated_by)"
                    + " VALUES (1,'TOTAL','重复','WARN','a','a')");
            try (var rows = statement.executeQuery("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = CURRENT_SCHEMA AND TABLE_NAME = 'OPS_ACCOUNT_MONITOR_FAMILY'"
                    + " ORDER BY ORDINAL_POSITION")) {
                List<String> columns = new ArrayList<>();
                while (rows.next()) columns.add(rows.getString(1).toLowerCase(java.util.Locale.ROOT));
                assertEquals(List.of("id", "config_id", "family_code", "family_name", "description", "batch_size", "source_type",
                        "enabled", "deleted", "sort_order", "created_by", "updated_by", "created_at", "updated_at"), columns);
            }
            try (var rows = statement.executeQuery("""
                    SELECT k.COLUMN_NAME
                    FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS c
                    JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE k
                      ON c.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA AND c.CONSTRAINT_NAME = k.CONSTRAINT_NAME
                    WHERE c.TABLE_SCHEMA = CURRENT_SCHEMA AND c.TABLE_NAME = 'OPS_ACCOUNT_MONITOR_FAMILY'
                      AND c.CONSTRAINT_NAME = 'UK_OPS_ACCOUNT_MONITOR_FAMILY' AND c.CONSTRAINT_TYPE = 'UNIQUE'
                    ORDER BY k.ORDINAL_POSITION
                    """)) {
                List<String> keyColumns = new ArrayList<>();
                while (rows.next()) keyColumns.add(rows.getString(1));
                assertEquals(List.of("CONFIG_ID", "FAMILY_CODE"), keyColumns);
            }
        }
    }

    @Test
    void replacesKnownDataGuideFragmentOnceAndLeavesOtherRowsAlone() throws Exception {
        String guideUpdate = statements().get(2);
        String untouched = "管理员自行维护的数据指南，不含已知片段";
        String once = "前缀。" + NEW_FRAGMENT + "（内置目录为种子，可在业务监测配置详情启停、改级别或新增自定义规则组），SQL 为 func_call_sql 的 AI_* 模板，不使用旧 ops_account_rule_config；该类型仅手动或明确任务执行，不能按 check_time 推断漏调度。后缀";
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE sys_config_item (id INT PRIMARY KEY, config_group VARCHAR(64), config_key VARCHAR(64), plain_value TEXT)");
            statement.execute("INSERT INTO sys_config_item VALUES (1,'agent.runtime','dataGuide','前缀。" + OLD_FRAGMENT + "。后缀')");
            statement.execute("INSERT INTO sys_config_item VALUES (2,'agent.runtime','dataGuide','" + untouched + "')");
            statement.execute("INSERT INTO sys_config_item VALUES (3,'other.group','dataGuide','前缀。" + OLD_FRAGMENT + "。后缀')");
            // 首次命中并替换；片段已消失后再次执行不改动任何行。
            assertEquals(1, statement.executeUpdate(guideUpdate));
            assertEquals(0, statement.executeUpdate(guideUpdate));
            try (var rows = statement.executeQuery("SELECT plain_value FROM sys_config_item ORDER BY id")) {
                assertTrue(rows.next());
                assertEquals(once, rows.getString(1));
                assertTrue(rows.next());
                assertEquals(untouched, rows.getString(1));
                assertTrue(rows.next());
                assertEquals("前缀。" + OLD_FRAGMENT + "。后缀", rows.getString(1));
                assertFalse(rows.next());
            }
        }
    }

    private static void assertRejected(java.sql.Statement statement, String sql) throws Exception {
        try {
            statement.execute(sql);
        } catch (java.sql.SQLException expected) {
            return;
        }
        throw new AssertionError("唯一键未生效：" + sql);
    }
}
