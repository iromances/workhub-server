package cn.aslight.workhub.maintenance;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.internal.parser.ParsingContext;
import org.flywaydb.core.internal.resource.StringResource;
import org.flywaydb.database.mysql.MySQLParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用 Flyway 生产同款 MySQL 解析器校验系统集成迁移可被解析，排查真实库迁移失败原因。
 */
class IntegrationCatalogMigrationParseTest {

    private static final String MIGRATION = "V20261106_000001__add_integration_catalog.sql";

    @Test
    void flywayMySqlParserAcceptsMigration() throws Exception {
        List<String> statements = statements();
        assertEquals(6, statements.size());
        assertTrue(statements.get(0).startsWith("CREATE TABLE IF NOT EXISTS `sys_integration_system`"));
        assertTrue(statements.get(1).startsWith("CREATE TABLE IF NOT EXISTS `sys_integration_capability`"));
        assertTrue(statements.get(2).startsWith("INSERT INTO `sys_integration_system`"));
    }

    private List<String> statements() throws Exception {
        String sql;
        try (var input = getClass().getResourceAsStream("/db/schema/mysql/" + MIGRATION)) {
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        var parser = new MySQLParser(new FluentConfiguration().placeholderReplacement(false), new ParsingContext());
        List<String> statements = new ArrayList<>();
        try (var iterator = parser.parse(new StringResource(sql))) {
            while (iterator.hasNext()) {
                statements.add(iterator.next().getSql().replaceFirst("\\A(?:\\s*--[^\\n]*(?:\\n|$))*\\s*", "").strip());
            }
        }
        return statements;
    }
}
