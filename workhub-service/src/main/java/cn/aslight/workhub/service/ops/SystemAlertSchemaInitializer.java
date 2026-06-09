package cn.aslight.workhub.service.ops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

@Service
public class SystemAlertSchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(SystemAlertSchemaInitializer.class);
    private static final String SUBSYSTEM_TABLE = "ops_system_alert_subsystem";
    private static final String EVENT_TABLE = "ops_system_alert_event";

    private final DataSource dataSource;
    private volatile boolean initialized;

    public SystemAlertSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            try (Connection connection = dataSource.getConnection()) {
                ensureSubsystemTable(connection);
                ensureEventTable(connection);
                initialized = true;
            } catch (SQLException ex) {
                throw new IllegalStateException("初始化系统预警表结构失败：" + ex.getMessage(), ex);
            }
        }
    }

    private void ensureSubsystemTable(Connection connection) throws SQLException {
        if (tableExists(connection, SUBSYSTEM_TABLE)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE `ops_system_alert_subsystem` (
                      `id` BIGINT NOT NULL AUTO_INCREMENT,
                      `business_line_code` VARCHAR(128) NOT NULL,
                      `environment_code` VARCHAR(64) NOT NULL,
                      `subsystem_name` VARCHAR(128) NOT NULL,
                      `service_name` VARCHAR(128) NOT NULL,
                      `enabled` TINYINT(1) NOT NULL DEFAULT 1,
                      `remark` VARCHAR(255) NULL,
                      `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      PRIMARY KEY (`id`),
                      UNIQUE KEY `uk_ops_system_alert_subsystem` (`business_line_code`, `environment_code`, `service_name`),
                      KEY `idx_ops_system_alert_subsystem_list` (`business_line_code`, `environment_code`, `enabled`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            log.warn("Created missing table {}", SUBSYSTEM_TABLE);
        }
    }

    private void ensureEventTable(Connection connection) throws SQLException {
        if (tableExists(connection, EVENT_TABLE)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE `ops_system_alert_event` (
                      `id` BIGINT NOT NULL AUTO_INCREMENT,
                      `business_line_code` VARCHAR(128) NOT NULL,
                      `environment_code` VARCHAR(64) NOT NULL,
                      `subsystem_name` VARCHAR(128) NULL,
                      `service_name` VARCHAR(128) NOT NULL,
                      `log_level` VARCHAR(32) NOT NULL,
                      `title` VARCHAR(255) NULL,
                      `message` TEXT NULL,
                      `error_type` VARCHAR(255) NULL,
                      `stack_trace` MEDIUMTEXT NULL,
                      `trace_id` VARCHAR(128) NULL,
                      `request_id` VARCHAR(128) NULL,
                      `occurred_at` DATETIME NOT NULL,
                      `source_type` VARCHAR(32) NOT NULL DEFAULT 'LOCAL',
                      `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (`id`),
                      KEY `idx_ops_system_alert_event_query` (`business_line_code`, `environment_code`, `service_name`, `log_level`, `occurred_at`),
                      KEY `idx_ops_system_alert_event_time` (`occurred_at`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            log.warn("Created missing table {}", EVENT_TABLE);
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        return tableExists(metaData, connection.getCatalog(), tableName)
                || tableExists(metaData, connection.getCatalog(), tableName.toUpperCase(Locale.ROOT))
                || tableExists(metaData, connection.getCatalog(), tableName.toLowerCase(Locale.ROOT));
    }

    private boolean tableExists(DatabaseMetaData metaData, String catalog, String tableName) throws SQLException {
        try (ResultSet resultSet = metaData.getTables(catalog, null, tableName, new String[]{"TABLE"})) {
            return resultSet.next();
        }
    }
}
