package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.AgentMapper;
import cn.aslight.workhub.dao.agent.AgentToolMapper;
import cn.aslight.workhub.dao.project.BusinessLineMapper;
import cn.aslight.workhub.model.agent.AgentToolAdminResponse;
import cn.aslight.workhub.model.agent.AgentToolSaveRequest;
import cn.aslight.workhub.model.system.SysUserEntity;
import cn.aslight.workhub.service.auth.PermissionContextService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.apache.ibatis.session.Configuration;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 用真实迁移、生产 Mapper 验证工具文案目录合并、乐观锁保存与审计；不连接业务数据库。 */
class AgentToolAdminServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final Map<Long, List<String>> grants = new HashMap<>(Map.of(1L,
            List.of("agent:view", "agent:manage", "work-item:view", "agent:code:use"), 2L, List.of("agent:view")));
    private JdbcTemplate jdbc;
    private AgentToolMapper registrations;
    private AgentToolAdminService service;
    private AgentToolCatalog catalog;

    @BeforeEach
    void setUp() throws Exception {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:agent_tools_" + System.nanoTime() + ";MODE=MySQL;LOCK_TIMEOUT=5000");
        var connection = dataSource.getConnection();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE sys_permission(permission_code VARCHAR(100) PRIMARY KEY,permission_name VARCHAR(100),permission_type VARCHAR(30),parent_code VARCHAR(100),route_path VARCHAR(200),sort_order INT,enabled INT,remark VARCHAR(500))");
        jdbc.execute("CREATE TABLE sys_role(id BIGINT PRIMARY KEY,role_code VARCHAR(100))");
        jdbc.execute("CREATE TABLE sys_role_permission(role_id BIGINT,permission_code VARCHAR(100),UNIQUE(role_id,permission_code))");
        jdbc.update("INSERT INTO sys_role VALUES (1,'SUPER_ADMIN')");
        var configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(AgentMapper.class);
        configuration.addMapper(AgentToolMapper.class);
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        var session = new SqlSessionTemplate(factory.getObject());
        var agentMapper = session.getMapper(AgentMapper.class);
        registrations = session.getMapper(AgentToolMapper.class);
        var permissions = mock(PermissionContextService.class);
        when(permissions.requireActiveUser(anyString())).thenAnswer(invocation -> {
            String name = invocation.getArgument(0);
            var user = new SysUserEntity();
            user.setId(name.equals("owner") ? 1L : 2L);
            user.setUserName(name);
            return user;
        });
        when(permissions.permissionCodes(anyLong())).thenAnswer(invocation -> grants.get(invocation.getArgument(0)));
        var access = new AgentAccessService(permissions, mock(BusinessLineMapper.class), agentMapper, json);
        catalog = new AgentToolCatalog(List.of(tool("workhub.bugs.list", true), tool("code.write", false), tool("future.tool", false)));
        service = new AgentToolAdminService(catalog, registrations, access, agentMapper, json);
        ScriptUtils.executeSqlScript(connection, new FileSystemResource(
                Path.of("../workhub-bootstrap/src/main/resources/db/schema/mysql/V20261104_014200__add_agent_workbench.sql")));
        ScriptUtils.executeSqlScript(connection, new FileSystemResource(
                Path.of("../workhub-bootstrap/src/main/resources/db/schema/mysql/V20261107_120000__add_agent_tool_catalog.sql")));
    }

    private AgentTool tool(String code, boolean readOnly) {
        return new AgentTool() {
            public String code() { return code; }
            public String description() { return "AI 目录文案 " + code; }
            public JsonNode inputSchema() { return json.readTree("{}"); }
            public Set<String> permissions() { return Set.of("work-item:view"); }
            public boolean readOnly() { return readOnly; }
            public Object execute(AgentToolContext context, JsonNode arguments) { return null; }
        };
    }

    @Test
    void seedLoadsAllFiftyToolsWithCuratedLabels() {
        assertEquals(50, registrations.list().size());
        var seeded = registrations.find("verification.record");
        assertEquals("记录复查样本", seeded.name());
    }

    @Test
    void listMergesCatalogSeedFallbackAndRetiredRows() {
        jdbc.update("INSERT INTO pm_agent_tool(code,name,description,version,updated_by,created_at,updated_at) VALUES ('ghost.tool','幽灵','已下线示例',0,'x',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        var rows = service.list("owner");
        // 目录 3 项 + 目录未收录的种子行 48 项 + ghost.tool 1 项；测试目录只是生产目录的子集，余量按已下线展示。
        assertEquals(52, rows.size());
        assertEquals(rows.size(), rows.stream().map(AgentToolAdminResponse::code).distinct().count());
        var seeded = rows.stream().filter(row -> row.code().equals("workhub.bugs.list")).findFirst().orElseThrow();
        assertEquals("缺陷列表", seeded.name());
        assertTrue(seeded.maintained());
        assertFalse(seeded.retired());
        assertTrue(seeded.readOnly());
        var fallback = rows.stream().filter(row -> row.code().equals("future.tool")).findFirst().orElseThrow();
        assertEquals("future.tool", fallback.name());
        assertEquals("", fallback.description());
        assertFalse(fallback.maintained());
        var ghost = rows.stream().filter(row -> row.code().equals("ghost.tool")).findFirst().orElseThrow();
        assertTrue(ghost.retired());
    }

    @Test
    void updateSavesLabelsIncrementsVersionAndWritesAudit() {
        var updated = service.update("workhub.bugs.list",
                new AgentToolSaveRequest(0L, " 缺陷清单 ", "业务线缺陷工作项列表"), "owner");
        assertEquals("缺陷清单", updated.name());
        assertEquals(1, updated.version());
        var row = registrations.find("workhub.bugs.list");
        assertEquals(1, row.version());
        assertEquals("owner", row.updatedBy());
        var audits = jdbc.queryForList("SELECT object_type,object_id,action,actor FROM pm_agent_audit WHERE object_type='AGENT_TOOL'");
        assertEquals(1, audits.size());
        assertEquals("workhub.bugs.list", audits.getFirst().get("object_id"));
        assertEquals("UPDATE", audits.getFirst().get("action"));
    }

    @Test
    void updateInsertsRegistrationForUnmaintainedCatalogTool() {
        var updated = service.update("future.tool", new AgentToolSaveRequest(0L, "新工具", "新工具说明"), "owner");
        assertEquals("新工具", updated.name());
        // 首次保存插入新行，版本保持 0；后续更新才递增。
        assertEquals(0, updated.version());
        assertNotNull(registrations.find("future.tool"));
    }

    @Test
    void updateRejectsStaleVersion() {
        assertThrows(IllegalStateException.class,
                () -> service.update("workhub.bugs.list", new AgentToolSaveRequest(5L, "新名", "新描述"), "owner"));
    }

    @Test
    void updateRejectsCodeMissingFromCatalog() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> service.update("not.a.tool", new AgentToolSaveRequest(0L, "新名", "新描述"), "owner"));
        assertTrue(ex.getMessage().contains("not.a.tool"));
    }

    @Test
    void updateRequiresManagePermission() {
        assertThrows(AccessDeniedException.class,
                () -> service.update("workhub.bugs.list", new AgentToolSaveRequest(0L, "新名", "新描述"), "viewer"));
    }
}
