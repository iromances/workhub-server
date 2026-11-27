package cn.aslight.workhub.dao.attachment;

import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 在内存数据库执行待办截图用到的附件 SQL，核对业务类型、业务 ID 范围与排序。
 */
class AttachmentMapperTodoScreenshotSqlTest {

    private static final String TODO = AttachmentMapper.class.getName() + ".";

    private Connection connection;
    private Configuration configuration;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
        update("""
                CREATE TABLE pm_attachment (
                    id BIGINT PRIMARY KEY, biz_type VARCHAR(32) NOT NULL, biz_id BIGINT NOT NULL,
                    file_name VARCHAR(255) NOT NULL, storage_path VARCHAR(512), content_type VARCHAR(128),
                    file_content BLOB, file_size BIGINT, file_sha256 CHAR(64), created_at TIMESTAMP
                )
                """);
        configuration = new Configuration();
        configuration.addMapper(AttachmentMapper.class);

        insert(1L, "INTAKE_TODO_SCREENSHOT", 501L, "2026-06-04 10:00:00");
        insert(2L, "INTAKE_SCREENSHOT", 501L, "2026-06-04 10:00:00");
        insert(3L, "INTAKE_TODO_SCREENSHOT", 502L, "2026-06-04 09:00:00");
        insert(4L, "INTAKE_TODO_SCREENSHOT", 9001L, "2026-06-04 10:00:00");
        insert(5L, "INTAKE_TODO_SCREENSHOT", 501L, "2026-06-04 10:00:00");
    }

    @AfterEach
    void close() throws Exception {
        connection.close();
    }

    @Test
    void findByBizIdsShouldReturnOnlyRequestedTypeAndBusinessInUploadOrder() throws Exception {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("bizType", "INTAKE_TODO_SCREENSHOT");
        parameters.put("bizIds", List.of(501L, 502L));

        var bound = configuration.getMappedStatement(TODO + "findByBizIds").getBoundSql(parameters);

        // 业务类型 1 个 + 业务 ID 每个待办 1 个
        assertEquals(3, bound.getParameterMappings().size());
        assertFalse(bound.getSql().contains("file_content"));
        assertEquals(List.of(1L, 5L, 3L), queryIds("findByBizIds", parameters));
    }

    @Test
    void deleteByBizAndIdsShouldOnlyDeleteListedIdsOfThatTodo() throws Exception {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("bizType", "INTAKE_TODO_SCREENSHOT");
        parameters.put("bizId", 501L);
        parameters.put("ids", List.of(1L, 3L));

        // 3 属于另一个待办：不删除
        assertEquals(1, update("deleteByBizAndIds", parameters));
        assertEquals(List.of(2L, 3L, 4L, 5L), remainingIds());

        parameters.put("ids", List.of(3L));
        assertEquals(0, update("deleteByBizAndIds", parameters));
        assertEquals(List.of(2L, 3L, 4L, 5L), remainingIds());
    }

    @Test
    void deleteByBizShouldRemoveAllAttachmentsOfOneBusinessObject() throws Exception {
        assertEquals(2, update("deleteByBiz", Map.of("bizType", "INTAKE_TODO_SCREENSHOT", "bizId", 501L)));
        assertEquals(List.of(2L, 3L, 4L), remainingIds());

        assertEquals(1, update("deleteByBiz", Map.of("bizType", "INTAKE_SCREENSHOT", "bizId", 501L)));
        assertEquals(List.of(3L, 4L), remainingIds());
    }

    private void insert(long id, String bizType, long bizId, String createdAt) throws Exception {
        update("INSERT INTO pm_attachment(id, biz_type, biz_id, file_name, file_content, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                id, bizType, bizId, "截图" + id + ".png", new byte[]{1, 2, 3}, createdAt);
    }

    private List<Long> remainingIds() throws Exception {
        List<Long> ids = new ArrayList<>();
        try (var statement = connection.prepareStatement("SELECT id FROM pm_attachment ORDER BY id");
             var result = statement.executeQuery()) {
            while (result.next()) {
                ids.add(result.getLong(1));
            }
        }
        return ids;
    }

    private List<Long> queryIds(String statementId, Map<String, Object> parameters) throws Exception {
        MappedStatement mappedStatement = configuration.getMappedStatement(TODO + statementId);
        var bound = mappedStatement.getBoundSql(parameters);
        try (var statement = connection.prepareStatement(bound.getSql())) {
            new DefaultParameterHandler(mappedStatement, parameters, bound).setParameters(statement);
            try (var result = statement.executeQuery()) {
                List<Long> ids = new ArrayList<>();
                while (result.next()) {
                    ids.add(result.getLong(1));
                }
                return ids;
            }
        }
    }

    private int update(String statementId, Map<String, Object> parameters) throws Exception {
        MappedStatement mappedStatement = configuration.getMappedStatement(TODO + statementId);
        var bound = mappedStatement.getBoundSql(parameters);
        try (var statement = connection.prepareStatement(bound.getSql())) {
            new DefaultParameterHandler(mappedStatement, parameters, bound).setParameters(statement);
            return statement.executeUpdate();
        }
    }

    private void update(String sql, Object... values) throws Exception {
        try (var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            statement.executeUpdate();
        }
    }
}
