package cn.aslight.workhub.maintenance;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.internal.parser.ParsingContext;
import org.flywaydb.core.internal.resource.StringResource;
import org.flywaydb.database.mysql.MySQLParser;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class AgentMonitorEvidenceMigrationTest {
    @Test void supportsFreshAndExistingAuditTableAndPreservesHistory() throws Exception {
        String sql;
        try (var input = getClass().getResourceAsStream("/db/schema/mysql/V20261119_180000__agent_monitor_execution_evidence.sql")) {
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        var parser = new MySQLParser(new FluentConfiguration().placeholderReplacement(false), new ParsingContext());
        var statements = new ArrayList<String>();
        try (var it = parser.parse(new StringResource(sql))) { while (it.hasNext()) statements.add(it.next().getSql()); }
        assertEquals(4, statements.size());
        assertTrue(statements.get(0).contains("PRIMARY KEY (agent_run_id, config_id)"));
        assertTrue(statements.get(1).contains("CREATE TABLE IF NOT EXISTS func_call_sql_audit_log"));
        assertTrue(statements.get(2).contains("monitor_run_id"));
        assertFalse(sql.contains("DELETE FROM"));
        assertTrue(statements.get(3).contains("REPLACE(plain_value"));
    }
}
