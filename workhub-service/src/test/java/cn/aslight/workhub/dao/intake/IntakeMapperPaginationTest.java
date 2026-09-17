package cn.aslight.workhub.dao.intake;

import cn.aslight.workhub.model.intake.IntakeListQuery;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 在内存数据库执行生产环境实际使用的筛选、排序和分页 SQL 片段。
 * MySQL JSON 展示投影不在此测试范围内。
 */
class IntakeMapperPaginationTest {
    private Connection connection;
    private Configuration configuration;
    private MappedStatement pageStatement;

    @BeforeEach
    void setUp() throws Exception {
        // 与 MySQL 的无长度 BINARY 转换保持一致，避免 H2 默认只保留一个字节。
        connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;VARIABLE_BINARY=TRUE");
        update("""
                CREATE TABLE pm_intake_record (
                    id BIGINT PRIMARY KEY, deleted INT DEFAULT 0, intake_status VARCHAR(32) DEFAULT '待整理',
                    demand_status VARCHAR(32), priority VARCHAR(16), received_at TIMESTAMP,
                    requirement_name VARCHAR(255), requirement_digest VARCHAR(255),
                    requirement_summary VARCHAR(2000), remark VARCHAR(2000),
                    approval_code VARCHAR(64), proposer_name VARCHAR(128),
                    business_line_code VARCHAR(32), requirement_type VARCHAR(32), released_date DATE,
                    structured_data_json VARCHAR(2000), raw_content VARCHAR(2000)
                )
                """);
        update("CREATE TABLE pm_business_line (business_line_code VARCHAR(32), business_line_name VARCHAR(128))");
        configuration = new Configuration();
        configuration.addMapper(IntakeMapper.class);
        var source = new XMLLanguageDriver().createSqlSource(configuration,
                "<script>SELECT id FROM (" + IntakeMapper.PAGE_RECORDS_SQL + ") pm_intake_record "
                        + IntakeMapper.LIST_ORDER_BY + "</script>", Map.class);
        pageStatement = new MappedStatement.Builder(configuration, "pageIds", source, SqlCommandType.SELECT).build();
    }

    @AfterEach
    void close() throws Exception {
        connection.close();
    }

    @Test
    void shouldSortByStatusBeforePriorityAndPaginateAfterSorting() throws Exception {
        insert(1, "待澄清", "低", "2026-09-01 10:00:00");
        insert(2, "终止关闭", "高", "2026-09-01 10:00:00");
        insert(3, "已收录", "中", "2026-09-01 10:00:00");
        insert(4, "待澄清", null, "2026-09-01 10:00:00");
        insert(5, "开发中", "未知", "2026-09-01 10:00:00");
        insert(6, "已收录", " ", "2026-09-01 10:00:00");
        insert(7, "待澄清", "高", "2026-09-02 10:00:00");
        update("UPDATE pm_intake_record SET deleted = 1 WHERE id = 7");

        assertEquals(List.of(1L, 4L, 5L, 3L, 6L, 2L), page(all(0, 20)));
        assertEquals(List.of(1L, 4L), page(all(0, 2)));
        assertEquals(List.of(5L, 3L), page(all(2, 2)));
        assertEquals(List.of(6L, 2L), page(all(4, 2)));
        assertEquals(6L, count(all(2, 2)));
        assertEquals(List.of(), page(all(6, 2)));
    }

    @Test
    void shouldKeepConfiguredStatusOrderWithinSamePriority() throws Exception {
        List<String> statuses = List.of("待澄清", "待处理", "处理中", "待评估", "待排期", "待设计", "开发中",
                "测试中", "待验收", "待上线", "已收录", "已暂停", "已完成", "终止关闭");
        List<Long> expected = new ArrayList<>();
        for (int i = statuses.size() - 1; i >= 0; i--) {
            insert(i + 1, statuses.get(i), "中", "2026-09-01 10:00:00");
        }
        for (long i = 1; i <= statuses.size(); i++) {
            expected.add(i);
        }
        insert(15, null, "中", "2026-09-02 10:00:00");
        expected.add(15L);
        assertEquals(expected, page(all(0, 20)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"开发中", "已完成"})
    void shouldKeepSameStatusPriorityTimeAndIdOrder(String status) throws Exception {
        insert(1, status, "高", "2026-09-01 10:00:00");
        insert(2, status, "高", "2026-09-02 10:00:00");
        insert(3, status, "高", "2026-09-02 10:00:00");
        insert(4, status, "高", null);
        insert(5, status, "低", "2026-09-03 10:00:00");
        insert(6, "待澄清", "高", "2026-09-04 10:00:00");
        var query = new IntakeListQuery(null, null, null, null, null, null, status, null, null, 0, 20);

        assertEquals(List.of(3L, 2L, 1L, 4L, 5L), page(query));
        assertEquals(5, count(query));
    }

    @Test
    void shouldCombineAllFiltersAndUseInclusiveDateBounds() throws Exception {
        update("INSERT INTO pm_business_line VALUES ('BL001', '资产业务')");
        for (int id = 1; id <= 5; id++) {
            insert(id, "已完成", "中", "2026-09-01 10:00:00");
            update("""
                    UPDATE pm_intake_record SET requirement_name = '客户绑卡', requirement_digest = '流程优化',
                        approval_code = 'REQ-060001', proposer_name = '周拓', business_line_code = 'BL001',
                        requirement_type = '研发需求', released_date = ? WHERE id = ?
                    """, id == 5 ? null : LocalDate.of(2026, 9, id), id);
        }
        var query = new IntakeListQuery("待整理", "绑卡", "060001", "周", "BL001", "研发需求", "已完成",
                LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 3), 0, 20);
        assertEquals(List.of(3L, 2L), page(query));
        assertEquals(2, count(query));

        var nameQuery = new IntakeListQuery(null, "流程", null, null, "资产业务", null, null, null, null, 0, 20);
        assertEquals(5, count(nameQuery));
        assertEquals(5, page(nameQuery).size());
        var noMatch = new IntakeListQuery(null, null, null, null, "不存在业务线", null, null, null, null, 0, 20);
        assertEquals(0, count(noMatch));
        assertTrue(page(noMatch).isEmpty());
        var wrongType = new IntakeListQuery(null, null, null, null, null, "数据提取/运维", null, null, null, 0, 20);
        assertEquals(0, count(wrongType));
        var wrongStatus = new IntakeListQuery("已转换", null, null, null, null, null, null, null, null, 0, 20);
        assertEquals(0, count(wrongStatus));
    }

    @Test
    void shouldTreatWildcardsQuotesAndCaseAsLiteralKeywordText() throws Exception {
        insert(1, "开发中", "高", "2026-09-01 10:00:00");
        insert(2, "开发中", "高", "2026-09-01 10:00:00");
        String keyword = "ABC%_' OR 1=1 --";
        update("UPDATE pm_intake_record SET requirement_name = ? WHERE id = 1", "需求" + keyword);
        update("UPDATE pm_intake_record SET requirement_name = ? WHERE id = 2", keyword.toLowerCase());
        var query = new IntakeListQuery(null, keyword, null, null, null, null, null, null, null, 0, 20);
        assertEquals(List.of(1L), page(query));
        assertEquals(1, count(query));
    }

    @Test
    void shouldUseOnlyFormalFieldsForFiltering() throws Exception {
        insert(1, "开发中", "高", "2026-09-01 10:00:00");
        update("UPDATE pm_intake_record SET structured_data_json = ?, raw_content = ? WHERE id = 1",
                "{\"requirementName\":\"历史名称\",\"releasedTime\":\"2026/09/01\"}", "需求名称：历史名称");
        var query = new IntakeListQuery(null, "历史名称", null, null, null, null, null, null, null, 0, 20);
        assertEquals(0, count(query));
        assertTrue(page(query).isEmpty());
        update("UPDATE pm_intake_record SET requirement_name = '正式名称' WHERE id = 1");
        var formalQuery = new IntakeListQuery(null, "正式名称", null, null, null, null, null, null, null, 0, 20);
        assertEquals(List.of(1L), page(formalQuery));
        assertEquals(1, count(formalQuery));
    }

    @Test
    void fullPageStatementShouldBindPaginationAndCountShouldAvoidDisplaySubqueries() {
        var parameters = Map.of("query", all(20, 10));
        var bound = configuration.getMappedStatement(IntakeMapper.class.getName() + ".findPage").getBoundSql(parameters);
        assertEquals(List.of("query.pageSize", "query.offset"),
                bound.getParameterMappings().stream().map(item -> item.getProperty()).toList());
        assertTrue(bound.getSql().contains("FROM ( SELECT * FROM pm_intake_record"));
        String countSql = configuration.getMappedStatement(IntakeMapper.class.getName() + ".count").getBoundSql(parameters).getSql();
        assertFalse(countSql.contains("pm_intake_todo"));
        assertFalse(countSql.contains("pm_intake_development_analysis"));
        assertFalse(countSql.contains("ORDER BY"));
    }

    @Test
    void searchKeywordShouldMatchCaseInsensitivelyBeforeApplyingLimit() throws Exception {
        insert(1, "开发中", "高", "2026-09-03 10:00:00");
        insert(2, "开发中", "中", "2026-09-02 10:00:00");
        insert(3, "开发中", "低", "2026-09-01 10:00:00");
        update("UPDATE pm_intake_record SET remark = '支持 MCP 查询' WHERE id IN (2, 3)");
        var query = new IntakeListQuery(null, null, null, null, null, null, null, null, null, 0, 1, "mcp");
        assertEquals(List.of(2L), page(query));
        assertEquals(2, count(query));
    }

    private IntakeListQuery all(long offset, int pageSize) {
        return new IntakeListQuery(null, null, null, null, null, null, null, null, null, offset, pageSize);
    }

    private void insert(long id, String status, String priority, String receivedAt) throws Exception {
        update("INSERT INTO pm_intake_record(id, demand_status, priority, received_at) VALUES (?, ?, ?, ?)",
                id, status, priority, receivedAt);
    }

    private void update(String sql, Object... values) throws Exception {
        try (var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            statement.executeUpdate();
        }
    }

    private List<Long> page(IntakeListQuery query) throws Exception {
        return execute(pageStatement, query);
    }

    private long count(IntakeListQuery query) throws Exception {
        return execute(configuration.getMappedStatement(IntakeMapper.class.getName() + ".count"), query).getFirst();
    }

    private List<Long> execute(MappedStatement mappedStatement, IntakeListQuery query) throws Exception {
        Map<String, Object> parameters = Map.of("query", query);
        var bound = mappedStatement.getBoundSql(parameters);
        try (var statement = connection.prepareStatement(bound.getSql())) {
            new DefaultParameterHandler(mappedStatement, parameters, bound).setParameters(statement);
            try (var result = statement.executeQuery()) {
                List<Long> values = new ArrayList<>();
                while (result.next()) {
                    values.add(result.getLong(1));
                }
                return values;
            }
        }
    }
}
