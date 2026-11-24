package cn.aslight.workhub.mcp.db;

import org.junit.jupiter.api.Test;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;

import java.sql.Types;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseDiagnosticServiceTest {

    @Test
    void jdbcUrl_shouldOmitDatabasePathWhenSchemaIsBlank() {
        String url = DatabaseDiagnosticService.jdbcUrl("127.0.0.1", 3306, null);

        assertEquals(
                "jdbc:mysql://127.0.0.1:3306/?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false&connectTimeout=10000&socketTimeout=60000",
                url
        );
    }

    @Test
    void resultSetToRows_shouldMaskBySourceColumnEvenWhenAliasIsGeneric() throws Exception {
        CachedRowSet rowSet = RowSetProvider.newFactory().createCachedRowSet();
        RowSetMetaDataImpl metaData = new RowSetMetaDataImpl();
        metaData.setColumnCount(3);
        configureColumn(metaData, 1, "display_value", "real_name", Types.VARCHAR);
        configureColumn(metaData, 2, "contact", "mobile_phone", Types.VARCHAR);
        configureColumn(metaData, 3, "project_id", "project_id", Types.BIGINT);
        rowSet.setMetaData(metaData);
        rowSet.moveToInsertRow();
        rowSet.updateString(1, "张三");
        rowSet.updateString(2, "13812345678");
        rowSet.updateLong(3, 42L);
        rowSet.insertRow();
        rowSet.moveToCurrentRow();
        rowSet.beforeFirst();

        DatabaseDiagnosticService service = new DatabaseDiagnosticService(null, null, null, null);
        Map<String, Object> result = service.resultSetToRows(rowSet, 10, 65536);
        @SuppressWarnings("unchecked")
        Map<String, Object> row = ((List<Map<String, Object>>) result.get("rows")).getFirst();

        assertEquals("张**", row.get("display_value"));
        assertEquals("138****5678", row.get("contact"));
        assertEquals(42L, row.get("project_id"));
        assertTrue((Boolean) result.get("dataMasked"));
    }

    @Test
    void internalRowsPreserveLargeSingleValueWhileDiagnosticBudgetStillTruncates() throws Exception {
        var service = new DatabaseDiagnosticService(null, null, null, null);
        String content = "中文任务执行失败详情".repeat(10000);
        try (var rows = longRows(content)) {
            var bounded = service.resultSetToRows(rows, 10, 65536);
            assertEquals(true, bounded.get("truncatedByBytes"));
            assertEquals(List.of(), bounded.get("rows"));
            rows.beforeFirst();
            var complete = service.resultSetToRows(rows, 10);
            assertEquals(false, complete.get("truncatedByBytes"));
            var data = (List<?>) complete.get("rows");
            assertEquals(1, data.size());
            assertEquals(content, ((Map<?, ?>) data.getFirst()).get("handle_msg"));
            assertEquals("138****5678", ((Map<?, ?>) data.getFirst()).get("mobile_phone"));
            assertEquals(true, complete.get("dataMasked"));
            rows.beforeFirst();
            assertEquals(true, service.resultSetToRows(rows, 10, 0).get("truncatedByBytes"));
        }
    }

    @Test
    void internalRowsPreserveWholeMultiRowResultAndKeepRowLimit() throws Exception {
        var service = new DatabaseDiagnosticService(null, null, null, null);
        String content = "中文执行详情".repeat(2000);
        try (var rows = longRows(content, content, content)) {
            var bounded = service.resultSetToRows(rows, 10, 65536);
            assertEquals(true, bounded.get("truncatedByBytes"));
            assertTrue(((List<?>) bounded.get("rows")).size() < 3);
            rows.beforeFirst();
            var complete = service.resultSetToRows(rows, 10);
            assertEquals(false, complete.get("truncatedByBytes"));
            assertEquals(3, ((List<?>) complete.get("rows")).size());
            for (var row : (List<?>) complete.get("rows")) assertEquals(content, ((Map<?, ?>) row).get("handle_msg"));
            rows.beforeFirst();
            assertEquals(1, ((List<?>) service.resultSetToRows(rows, 1).get("rows")).size());
        }
    }

    @Test
    void internalEmptyResultRemainsAValidReceipt() throws Exception {
        var service = new DatabaseDiagnosticService(null, null, null, null);
        try (var rows = longRows()) {
            var result = service.resultSetToRows(rows, 10);
            assertEquals(List.of(), result.get("rows"));
            assertEquals(false, result.get("truncatedByBytes"));
        }
    }

    private CachedRowSet longRows(String... messages) throws Exception {
        var rows = RowSetProvider.newFactory().createCachedRowSet();
        var meta = new RowSetMetaDataImpl();
        meta.setColumnCount(2);
        configureColumn(meta, 1, "handle_msg", "handle_msg", Types.VARCHAR);
        configureColumn(meta, 2, "mobile_phone", "mobile_phone", Types.VARCHAR);
        rows.setMetaData(meta);
        for (String message : messages) {
            rows.moveToInsertRow();
            rows.updateString(1, message);
            rows.updateString(2, "13812345678");
            rows.insertRow();
            rows.moveToCurrentRow();
        }
        rows.beforeFirst();
        return rows;
    }

    private void configureColumn(RowSetMetaDataImpl metaData,
                                 int index,
                                 String label,
                                 String name,
                                 int type) throws Exception {
        metaData.setColumnLabel(index, label);
        metaData.setColumnName(index, name);
        metaData.setColumnType(index, type);
        metaData.setColumnTypeName(index, type == Types.BIGINT ? "BIGINT" : "VARCHAR");
    }
}
