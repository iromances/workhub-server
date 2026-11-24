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

/** 验证现行迁移能被 Flyway MySQL 解析为单条权限更新。 */
class MonitorDisplayLabelsMigrationTest {
    @Test
    void parsesAsSinglePermissionUpdate() throws Exception {
        String sql;
        try (var input = getClass().getResourceAsStream(
                "/db/schema/mysql/V20261119_170000__rename_monitor_display_labels.sql")) {
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        var parser = new MySQLParser(new FluentConfiguration().placeholderReplacement(false), new ParsingContext());
        List<String> statements = new ArrayList<>();
        try (var iterator = parser.parse(new StringResource(sql))) {
            while (iterator.hasNext()) statements.add(iterator.next().getSql());
        }
        assertEquals(1, statements.size());
    }
}
