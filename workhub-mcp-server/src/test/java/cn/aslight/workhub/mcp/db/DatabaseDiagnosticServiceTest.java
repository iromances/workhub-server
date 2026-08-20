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
                "jdbc:mysql://127.0.0.1:3306/?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false",
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
