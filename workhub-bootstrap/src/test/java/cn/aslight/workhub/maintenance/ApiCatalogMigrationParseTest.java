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
 * 用 Flyway 生产同款 MySQL 解析器校验官方 API 接口目录迁移可被解析（含大体量 GitLab 种子）。
 */
class ApiCatalogMigrationParseTest {

    @Test
    void flywayMySqlParserAcceptsCatalogMigration() throws Exception {
        List<String> statements = statements("V20261113_120000__add_integration_api_catalog.sql");
        // 建表 + 禅道 3 个分块 INSERT + Jenkins 1 个 INSERT
        assertEquals(5, statements.size());
        assertTrue(statements.get(0).startsWith("CREATE TABLE IF NOT EXISTS `sys_integration_api_endpoint`"));
        assertTrue(statements.get(1).startsWith("INSERT INTO `sys_integration_api_endpoint`"));
        assertTrue(statements.stream().skip(1).allMatch(s -> s.startsWith("INSERT INTO")));
    }

    @Test
    void flywayMySqlParserAcceptsGitlabSeedMigration() throws Exception {
        List<String> statements = statements("V20261113_120100__add_integration_api_catalog_gitlab.sql");
        // 1761 条按 100 行分块
        assertEquals(16, statements.size());
        assertTrue(statements.stream().allMatch(s -> s.startsWith("INSERT INTO `sys_integration_api_endpoint`")));
    }

    private List<String> statements(String migration) throws Exception {
        String sql;
        try (var input = getClass().getResourceAsStream("/db/schema/mysql/" + migration)) {
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
