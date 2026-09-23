package cn.aslight.workhub.dao.intake;

import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 执行生产使用的局部 UPDATE 与行锁 SQL；MySQL JSON 类型迁移需另在目标环境验证。 */
class IntakeProcessInfoMapperTest {
    @Test
    void savesOnlyTargetFieldsSupportsClearingAndRollsBackTransaction() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;LOCK_TIMEOUT=100";
        try (Connection first = DriverManager.getConnection(url); Connection second = DriverManager.getConnection(url)) {
            first.createStatement().execute("""
                    CREATE TABLE pm_intake_record (
                      id BIGINT PRIMARY KEY, deleted INT DEFAULT 0, actual_effort VARCHAR(30), released_date DATE,
                      structured_data_json VARCHAR(4000), process_estimated_effort VARCHAR(4000),
                      demand_status VARCHAR(30), development_owner_user_name VARCHAR(30), updated_at TIMESTAMP)
                    """);
            first.createStatement().execute("INSERT INTO pm_intake_record (id, actual_effort, demand_status, development_owner_user_name) VALUES (19, '8h', '已完成', 'owner')");
            var configuration = new Configuration();
            configuration.addMapper(IntakeMapper.class);
            first.setAutoCommit(false);
            var lock = configuration.getMappedStatement(IntakeMapper.class.getName() + ".lockProcessInfo");
            var lockSql = lock.getBoundSql(Map.of("id", 19L));
            try (var statement = first.prepareStatement(lockSql.getSql())) {
                new DefaultParameterHandler(lock, Map.of("id", 19L), lockSql).setParameters(statement);
                assertTrue(statement.executeQuery().next());
            }
            assertThrows(SQLException.class, () -> second.createStatement().executeUpdate("UPDATE pm_intake_record SET actual_effort='99h' WHERE id=19"));
            var statement = configuration.getMappedStatement(IntakeMapper.class.getName() + ".updateProcessInfo");
            Map<String, Object> columns = new LinkedHashMap<>();
            columns.put("actual_effort", null);
            columns.put("released_date", LocalDate.of(2026, 9, 21));
            var parameters = new LinkedHashMap<String, Object>();
            parameters.put("id", 19L);
            parameters.put("formalChanges", columns);
            parameters.put("structuredJson", "{\"actualEffort\":null}");
            parameters.put("estimatedJson", "{\"developmentEstimatedEffort\":null,\"testingEstimatedEffort\":null,\"totalEstimatedEffort\":null}");
            var sql = statement.getBoundSql(parameters);
            try (var update = first.prepareStatement(sql.getSql())) {
                new DefaultParameterHandler(statement, parameters, sql).setParameters(update);
                assertEquals(1, update.executeUpdate());
            }
            try (var row = first.createStatement().executeQuery("SELECT * FROM pm_intake_record WHERE id=19")) {
                assertTrue(row.next());
                assertNull(row.getString("actual_effort"));
                assertEquals("2026-09-21", row.getString("released_date"));
                assertEquals(parameters.get("estimatedJson"), row.getString("process_estimated_effort"));
                assertEquals("已完成", row.getString("demand_status"));
                assertEquals("owner", row.getString("development_owner_user_name"));
            }
            first.rollback();
            try (var row = second.createStatement().executeQuery("SELECT actual_effort, process_estimated_effort FROM pm_intake_record WHERE id=19")) {
                assertTrue(row.next());
                assertEquals("8h", row.getString(1));
                assertNull(row.getString(2));
            }
        }
    }
}
