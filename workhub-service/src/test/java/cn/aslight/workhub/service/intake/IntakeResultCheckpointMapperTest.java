package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.dao.intake.DevelopmentAnalysisMapper;
import cn.aslight.workhub.dao.intake.IntakeClarificationAnalysisMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.apache.ibatis.session.Configuration;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class IntakeResultCheckpointMapperTest {
    @Test void migrationAndProductionMappersPreserveLargeOriginalAndScopeRecoveryToFailedSameIntake() throws Exception {
        var ds = new JdbcDataSource(); ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
        try (var connection = ds.getConnection()) {
            var jdbc = new JdbcTemplate(ds);
            for (String table : new String[]{"pm_intake_development_analysis", "pm_intake_clarification_analysis"})
                jdbc.execute("CREATE TABLE " + table + "(id BIGINT PRIMARY KEY,intake_id BIGINT,analysis_status VARCHAR(20))");
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("../workhub-bootstrap/src/main/resources/db/schema/mysql/V20261120_180000__intake_analysis_result_recovery.sql"));
            var config = new Configuration(); config.addMapper(DevelopmentAnalysisMapper.class); config.addMapper(IntakeClarificationAnalysisMapper.class);
            var factory = new SqlSessionFactoryBean(); factory.setDataSource(ds); factory.setConfiguration(config);
            var session = new SqlSessionTemplate(factory.getObject());
            var development = session.getMapper(DevelopmentAnalysisMapper.class);
            var clarification = session.getMapper(IntakeClarificationAnalysisMapper.class);
            String raw = "完整的需求与验收标准。".repeat(30000); String fingerprint = "a".repeat(64);
            for (String table : new String[]{"pm_intake_development_analysis", "pm_intake_clarification_analysis"})
                jdbc.update("INSERT INTO " + table + "(id,intake_id,analysis_status) VALUES(1,10,'RUNNING')");
            assertEquals(1, development.saveRawOutput(1L, fingerprint, raw));
            assertEquals(1, clarification.saveRawOutput(1L, fingerprint, raw));
            assertNull(development.findRecoverableOutput(10L, fingerprint));
            assertNull(clarification.findRecoverableOutput(10L, fingerprint));
            for (String table : new String[]{"pm_intake_development_analysis", "pm_intake_clarification_analysis"})
                jdbc.update("UPDATE " + table + " SET analysis_status='FAILED'");
            assertEquals(raw, development.findRecoverableOutput(10L, fingerprint));
            assertEquals(raw, clarification.findRecoverableOutput(10L, fingerprint));
            assertNull(development.findRecoverableOutput(11L, fingerprint));
            assertNull(clarification.findRecoverableOutput(10L, "b".repeat(64)));
            assertEquals(0, development.saveRawOutput(1L, fingerprint, "覆盖失败记录"));
            assertEquals(0, clarification.saveRawOutput(1L, fingerprint, "覆盖失败记录"));
            // 格式已完成但业务校验后来失败，不应永远复用同一份坏业务答案。
            for (String table : new String[]{"pm_intake_development_analysis", "pm_intake_clarification_analysis"})
                jdbc.update("UPDATE " + table + " SET analysis_status='RUNNING'");
            assertEquals(1, development.completeRawFormatting(1L));
            assertEquals(1, clarification.completeRawFormatting(1L));
            for (String table : new String[]{"pm_intake_development_analysis", "pm_intake_clarification_analysis"})
                jdbc.update("UPDATE " + table + " SET analysis_status='FAILED'");
            assertNull(development.findRecoverableOutput(10L, fingerprint));
            assertNull(clarification.findRecoverableOutput(10L, fingerprint));
            // 较新的任务已完成整理时，也不能退回更早的待恢复原文。
            for (String table : new String[]{"pm_intake_development_analysis", "pm_intake_clarification_analysis"}) {
                jdbc.update("UPDATE " + table + " SET recovery_pending=1");
                jdbc.update("INSERT INTO " + table + "(id,intake_id,analysis_status,input_fingerprint,recovery_pending) VALUES(2,10,'FAILED',?,0)", fingerprint);
            }
            assertNull(development.findRecoverableOutput(10L, fingerprint));
            assertNull(clarification.findRecoverableOutput(10L, fingerprint));
        }
    }
}
