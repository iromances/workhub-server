package cn.aslight.workhub.service.system;

import cn.aslight.workhub.dao.system.IntegrationCapabilityMapper;
import cn.aslight.workhub.dao.system.IntegrationSystemMapper;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 在 H2 MySQL 模式执行生产迁移，验证表结构、预置数据、权限授权与幂等性，以及 Mapper SQL 兼容性。
 */
class IntegrationCatalogMigrationTest {

    private static final String MIGRATION = "V20261106_000001__add_integration_catalog.sql";

    private Connection keepAlive;
    private JdbcTemplate jdbc;
    private SqlSessionFactory factory;

    @BeforeEach
    void setUp() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;LOCK_TIMEOUT=5000";
        keepAlive = DriverManager.getConnection(url);
        var source = new org.h2.jdbcx.JdbcDataSource();
        source.setURL(url);
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE sys_permission(permission_code VARCHAR(100) PRIMARY KEY,permission_name VARCHAR(100),permission_type VARCHAR(30),parent_code VARCHAR(100),route_path VARCHAR(200),sort_order INT,enabled INT,remark VARCHAR(500))");
        jdbc.execute("CREATE TABLE sys_role(id BIGINT PRIMARY KEY,role_code VARCHAR(100))");
        jdbc.execute("CREATE TABLE sys_role_permission(role_id BIGINT,permission_code VARCHAR(100),UNIQUE(role_id,permission_code))");
        jdbc.execute("INSERT INTO sys_role(id, role_code) VALUES (1, 'SUPER_ADMIN')");
        runMigration();
        var configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(IntegrationSystemMapper.class);
        configuration.addMapper(IntegrationCapabilityMapper.class);
        configuration.setEnvironment(new org.apache.ibatis.mapping.Environment("test",
                new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(), source));
        factory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @AfterEach
    void close() throws Exception {
        keepAlive.close();
    }

    private void runMigration() throws Exception {
        ScriptUtils.executeSqlScript(keepAlive,
                new FileSystemResource("../workhub-bootstrap/src/main/resources/db/schema/mysql/" + MIGRATION));
    }

    @Test
    void migrationPresetsThreeSystemsWithCapabilityCatalog() {
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM sys_integration_system", Integer.class));
        assertEquals(14, jdbc.queryForObject("SELECT COUNT(*) FROM sys_integration_capability", Integer.class));
        Map<String, Object> zentao = jdbc.queryForMap(
                "SELECT system_name, integration_method, auth_type, config_groups FROM sys_integration_system WHERE system_code = 'ZENTAO'");
        assertEquals("禅道", zentao.get("SYSTEM_NAME"));
        assertEquals("REST_API", zentao.get("INTEGRATION_METHOD"));
        assertEquals("ACCOUNT_PASSWORD", zentao.get("AUTH_TYPE"));
        assertEquals(List.of("创建任务", "创建测试单"), jdbc.queryForList(
                "SELECT capability_name FROM sys_integration_capability c JOIN sys_integration_system s ON c.system_id = s.id"
                        + " WHERE s.system_code = 'ZENTAO' AND c.capability_code IN ('create-test-task', 'create-task')"
                        + " ORDER BY c.capability_code", String.class));
        assertEquals(List.of("创建任务"), jdbc.queryForList(
                "SELECT capability_name FROM sys_integration_capability c JOIN sys_integration_system s ON c.system_id = s.id"
                        + " WHERE s.system_code = 'ZENTAO' AND c.impl_status = 'PLANNED'", String.class));
    }

    @Test
    void migrationGrantsMenuPermissionsToSuperAdmin() {
        assertEquals(3, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_permission WHERE permission_code LIKE 'system:integration:%'", Integer.class));
        assertEquals("MENU", jdbc.queryForObject(
                "SELECT permission_type FROM sys_permission WHERE permission_code = 'system:integration:view'", String.class));
        assertEquals("/system/integrations", jdbc.queryForObject(
                "SELECT route_path FROM sys_permission WHERE permission_code = 'system:integration:view'", String.class));
        assertEquals(3, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_role_permission rp JOIN sys_role r ON rp.role_id = r.id"
                        + " WHERE r.role_code = 'SUPER_ADMIN' AND rp.permission_code LIKE 'system:integration:%'", Integer.class));
    }

    @Test
    void migrationIsIdempotent() throws Exception {
        runMigration();
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM sys_integration_system", Integer.class));
        assertEquals(14, jdbc.queryForObject("SELECT COUNT(*) FROM sys_integration_capability", Integer.class));
        assertEquals(3, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_permission WHERE permission_code LIKE 'system:integration:%'", Integer.class));
    }

    @Test
    void mapperSqlWorksAgainstMigratedSchema() {
        try (SqlSession session = factory.openSession(true)) {
            var systems = session.getMapper(IntegrationSystemMapper.class);
            var capabilities = session.getMapper(IntegrationCapabilityMapper.class);
            assertEquals(3, systems.findAll().size());
            var zentao = systems.findByCode("ZENTAO");
            assertEquals(4, capabilities.findBySystemId(zentao.getId()).size());
            assertEquals(4L, capabilities.countBySystemId(zentao.getId()));
            assertEquals(zentao.getId(), systems.findById(zentao.getId()).getId());

            zentao.setSystemName("禅道平台");
            assertEquals(1, systems.update(zentao));
            assertEquals(4, capabilities.deleteBySystemId(zentao.getId()));
            assertEquals(0L, capabilities.countBySystemId(zentao.getId()));
        }
    }
}
