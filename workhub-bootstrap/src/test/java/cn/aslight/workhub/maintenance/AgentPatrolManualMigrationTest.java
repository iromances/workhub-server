package cn.aslight.workhub.maintenance;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.internal.parser.ParsingContext;
import org.flywaydb.core.internal.resource.StringResource;
import org.flywaydb.database.mysql.MySQLParser;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

/** REQ-6d1b5cb0：周期巡检任务统一改手动的订正迁移只做一条受限 UPDATE，不删除任何任务或运行历史。 */
class AgentPatrolManualMigrationTest {
    @Test void convertsAllCronTasksToManualWithSingleGuardedUpdate() throws Exception {
        String sql;
        try (var input = getClass().getResourceAsStream("/db/schema/mysql/V20261122_110000__agent_patrol_cron_tasks_to_manual.sql")) {
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        var parser = new MySQLParser(new FluentConfiguration().placeholderReplacement(false), new ParsingContext());
        var statements = new ArrayList<String>();
        try (var it = parser.parse(new StringResource(sql))) { while (it.hasNext()) statements.add(it.next().getSql()); }
        assertEquals(1, statements.size());
        var update = statements.getFirst();
        // 解析器会把前置行注释并入语句，仅断言语句体。
        assertTrue(update.contains("UPDATE pm_agent_task"));
        assertTrue(update.contains("schedule_mode = 'MANUAL'"));
        assertTrue(update.contains("cron_expression = NULL"));
        assertTrue(update.contains("schedule_enabled = 0"));
        assertTrue(update.contains("next_run_at = NULL"));
        assertTrue(update.contains("version = version + 1"));
        assertTrue(update.contains("updated_at = UTC_TIMESTAMP(6)"));
        assertTrue(update.contains("WHERE schedule_mode = 'CRON'"));
        assertFalse(sql.contains("DELETE FROM"));
        assertFalse(sql.contains("DROP"));
    }
}
