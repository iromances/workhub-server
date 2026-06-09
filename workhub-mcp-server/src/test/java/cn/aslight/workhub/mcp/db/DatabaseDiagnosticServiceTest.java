package cn.aslight.workhub.mcp.db;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatabaseDiagnosticServiceTest {

    @Test
    void jdbcUrl_shouldOmitDatabasePathWhenSchemaIsBlank() {
        String url = DatabaseDiagnosticService.jdbcUrl("127.0.0.1", 3306, null);

        assertEquals(
                "jdbc:mysql://127.0.0.1:3306/?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false",
                url
        );
    }
}
