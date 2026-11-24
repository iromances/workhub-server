package cn.aslight.workhub.dao.intake;

import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;

class DevelopmentAnalysisErrorStackTest {
    @Test void migrationAndMapperRoundTripStackWithoutChangingOldRows() throws Exception {
        String url = "jdbc:h2:mem:analysis_stack;MODE=MySQL;VARIABLE_BINARY=TRUE;DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            String init = Files.readString(Path.of("../workhub-bootstrap/src/main/resources/db/schema/mysql/V1__init.sql"));
            int start = init.indexOf("CREATE TABLE `pm_intake_development_analysis`");
            String table = init.substring(start, init.indexOf(";", start) + 1);
            statement.execute(table);
            statement.execute("ALTER TABLE pm_intake_development_analysis ADD started_at DATETIME");
            statement.execute("ALTER TABLE pm_intake_development_analysis ADD finished_at DATETIME");
            statement.execute("CREATE TABLE pm_business_line (business_line_code VARCHAR(32), business_line_name VARCHAR(128))");
            statement.execute("CREATE ALIAS JSON_VALID FOR 'cn.aslight.workhub.dao.intake.H2MySqlJsonSupport.jsonValid'");
            statement.execute("CREATE ALIAS JSON_REMOVE FOR 'cn.aslight.workhub.dao.intake.H2MySqlJsonSupport.jsonRemove'");
            statement.execute("CREATE ALIAS JSON_SET FOR 'cn.aslight.workhub.dao.intake.H2MySqlJsonSupport.jsonSet'");
            statement.execute("INSERT INTO pm_intake_development_analysis (intake_id,analysis_status,created_by,updated_by) VALUES (285,'FAILED','admin','admin')");
            statement.execute(Files.readString(Path.of("../workhub-bootstrap/src/main/resources/db/schema/mysql/V20261120_080000__development_analysis_error_stack.sql")));
        }
        var config = new Configuration(new Environment("test", new JdbcTransactionFactory(), new UnpooledDataSource("org.h2.Driver", url, null)));
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(DevelopmentAnalysisMapper.class);
        try (var session = new SqlSessionFactoryBuilder().build(config).openSession()) {
            var mapper = session.getMapper(DevelopmentAnalysisMapper.class);
            var entity = mapper.findLatestByIntakeId(285L);
            assertNull(entity.getErrorStack());
            String stack = "java.lang.IllegalStateException: failed\nCaused by: ZCode bytes=24001";
            entity.setErrorStack(stack);
            assertEquals(1, mapper.updateExecutionState(entity));
            assertEquals(stack, mapper.findById(entity.getId()).getErrorStack());
            assertEquals(stack, mapper.listByIntakeId(285L).getFirst().getErrorStack());
            entity.setErrorStack(null);
            entity.setAnalysisStatus("DRAFT");
            mapper.update(entity);
            assertNull(mapper.findById(entity.getId()).getErrorStack());
        }
    }
}
