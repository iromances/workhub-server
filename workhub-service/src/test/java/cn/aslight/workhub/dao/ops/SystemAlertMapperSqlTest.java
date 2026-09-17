package cn.aslight.workhub.dao.ops;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemAlertMapperSqlTest {

    @Test
    void exactMessageCleanupSql_shouldUseSqlOperatorsInsteadOfXmlEntities() throws Exception {
        Method method = SystemAlertMapper.class.getMethod(
                "findExactMessageCleanupEventBatch", String.class, long.class, long.class, int.class);
        String sql = String.join("\n", method.getAnnotation(Select.class).value());

        assertTrue(sql.contains("e.id > #{processedEventId}"));
        assertTrue(sql.contains("e.id <= #{maxEventId}"));
        assertFalse(sql.contains("&gt;"));
        assertFalse(sql.contains("&lt;"));
    }
}
