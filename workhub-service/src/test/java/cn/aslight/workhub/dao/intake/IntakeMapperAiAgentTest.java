package cn.aslight.workhub.dao.intake;

import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 真实 SQL 级验证：findById 必须把 ai_agent_id（及 development_standard）映射进实体，
 * updateAiAgent 可往返更新。防再次出现"列清单漏列导致字段永远为 null"的回归。
 */
class IntakeMapperAiAgentTest {

    private static final String URL = "jdbc:h2:mem:intake_ai_agent_test;MODE=MySQL;VARIABLE_BINARY=TRUE;DB_CLOSE_DELAY=-1";
    private static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void setUp() throws Exception {
        try (Connection connection = DriverManager.getConnection(URL); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ALIAS IF NOT EXISTS JSON_VALID FOR 'cn.aslight.workhub.dao.intake.H2MySqlJsonSupport.jsonValid'");
            statement.execute("CREATE ALIAS IF NOT EXISTS JSON_REMOVE FOR 'cn.aslight.workhub.dao.intake.H2MySqlJsonSupport.jsonRemove'");
            statement.execute("CREATE ALIAS IF NOT EXISTS JSON_SET FOR 'cn.aslight.workhub.dao.intake.H2MySqlJsonSupport.jsonSet'");
            statement.execute("""
                    CREATE TABLE pm_intake_record (
                        id BIGINT PRIMARY KEY, source_type VARCHAR(64), source_channel VARCHAR(64),
                        external_message_id VARCHAR(128), sender_name VARCHAR(128), received_at TIMESTAMP,
                        raw_content CLOB, development_owner_user_name VARCHAR(64), development_standard VARCHAR(32),
                        ai_agent_id VARCHAR(64), structured_data_json CLOB, ai_draft_json CLOB,
                        intake_status VARCHAR(32), demand_status VARCHAR(32), pause_previous_demand_status VARCHAR(32),
                        pause_reason VARCHAR(500), pause_date DATE, enrichment_status VARCHAR(32),
                        enrichment_error_summary VARCHAR(1000), enrichment_updated_at TIMESTAMP,
                        converted_work_item_id BIGINT, approval_code VARCHAR(64), approval_title VARCHAR(255),
                        approval_status VARCHAR(32), proposer_name VARCHAR(128), submitted_at DATETIME,
                        requirement_type VARCHAR(32), requirement_name VARCHAR(500), requirement_summary CLOB,
                        requirement_digest VARCHAR(500), department VARCHAR(128), business_line_code VARCHAR(32),
                        project_hint VARCHAR(255), development_branch_name VARCHAR(255), zentao_url VARCHAR(500),
                        remark CLOB, planned_due_date DATE, planned_development_start_date DATE,
                        planned_testing_start_date DATE, planned_release_date DATE, development_started_date DATE,
                        testing_started_date DATE, actual_completed_date DATE, scheduled_acceptance_date DATE,
                        actual_testing_completed_date DATE, acceptance_date DATETIME, released_date DATE,
                        closed_date DATETIME, close_reason VARCHAR(1000), estimated_effort VARCHAR(32),
                        process_estimated_effort VARCHAR(32), actual_effort VARCHAR(32), actual_testing_effort VARCHAR(32),
                        priority VARCHAR(16), urgency VARCHAR(16), deleted INT DEFAULT 0,
                        deleted_at DATETIME, deleted_by VARCHAR(64), created_at DATETIME, updated_at DATETIME
                    )
                    """);
            statement.execute("CREATE TABLE pm_intake_development_analysis (id BIGINT PRIMARY KEY, intake_id BIGINT, business_line_code VARCHAR(32), draft_json CLOB)");
            statement.execute("CREATE TABLE pm_business_line (business_line_code VARCHAR(32), business_line_name VARCHAR(128))");
            statement.execute("""
                    INSERT INTO pm_intake_record (id, source_type, source_channel, sender_name, raw_content,
                        development_owner_user_name, development_standard, ai_agent_id, intake_status, demand_status, deleted)
                    VALUES (277, '需求截图附件录入', '需求录入', 'admin', '需求内容',
                        'shihao', 'SDD', 'agent-dev-1', '待整理', '开发中', 0)
                    """);
        }
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(IntakeMapper.class);
        Environment environment = new Environment("test", new JdbcTransactionFactory(),
                new UnpooledDataSource("org.h2.Driver", URL, "", ""));
        configuration.setEnvironment(environment);
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection connection = DriverManager.getConnection(URL); Statement statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        }
    }

    @Test
    void findByIdMapsAiAgentIdAndDevelopmentStandard() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            var entity = session.getMapper(IntakeMapper.class).findById(277L);
            assertEquals("agent-dev-1", entity.getAiAgentId(), "findById 列清单必须包含 ai_agent_id 并映射到实体");
            assertEquals("SDD", entity.getDevelopmentStandard(), "findById 列清单必须包含 development_standard");
        }
    }

    @Test
    void updateAiAgentRoundTrips() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            IntakeMapper mapper = session.getMapper(IntakeMapper.class);
            assertEquals(1, mapper.updateAiAgent(277L, "agent-dev-2"));
            assertEquals("agent-dev-2", mapper.findById(277L).getAiAgentId());
            assertEquals(1, mapper.updateAiAgent(277L, null));
            assertNull(mapper.findById(277L).getAiAgentId());
            assertEquals(1, mapper.updateAiAgent(277L, "agent-dev-1"));
            session.commit();
        }
    }
}
