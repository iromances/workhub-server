package cn.aslight.workhub.service.ops;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MonitorDisplayLabelsMigrationTest {
    @Test
    void renamesWithoutRemovedToolTableAndIsRepeatable() throws Exception {
        List<String> statements = List.of(Files.readString(Path.of(
                "../workhub-bootstrap/src/main/resources/db/schema/mysql/V20261119_170000__rename_monitor_display_labels.sql")));
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE sys_permission (permission_code VARCHAR(64) PRIMARY KEY, permission_name VARCHAR(128))");
            statement.execute("INSERT INTO sys_permission VALUES ('ops:monitor:view','业务监测'),"
                    + "('ops:monitor:update','编辑业务监测'),('ops:accounting:view','账务监测'),"
                    + "('ops:accounting:run','账务监测手动对账'),('other:view','其他页面')");
            for (int attempt = 0; attempt < 2; attempt++) {
                for (String migration : statements) statement.execute(migration);
                try (var rows = statement.executeQuery("SELECT permission_name FROM sys_permission ORDER BY permission_code")) {
                    List<String> names = new ArrayList<>();
                    while (rows.next()) names.add(rows.getString(1));
                    assertEquals(List.of("业务监测手动对账", "业务监测", "编辑服务监测", "服务监测", "其他页面"), names);
                }
            }
        }
    }
}
