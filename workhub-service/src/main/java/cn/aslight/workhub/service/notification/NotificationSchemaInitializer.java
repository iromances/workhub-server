package cn.aslight.workhub.service.notification;

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
public class NotificationSchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(NotificationSchemaInitializer.class);
    private static final String TABLE_NAME = "sys_notification";

    private final DataSource dataSource;
    private volatile boolean initialized;

    public NotificationSchemaInitializer(DataSource dataSource) {
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
                ensureTable(connection);
                initialized = true;
            } catch (SQLException ex) {
                throw new IllegalStateException("初始化站内信表结构失败：" + ex.getMessage(), ex);
            }
        }
    }

    private void ensureTable(Connection connection) throws SQLException {
        if (tableExists(connection)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE `sys_notification` (
                      `id` BIGINT NOT NULL AUTO_INCREMENT,
                      `recipient_user_name` VARCHAR(64) NOT NULL,
                      `notification_type` VARCHAR(64) NOT NULL,
                      `title` VARCHAR(255) NOT NULL,
                      `content` TEXT NULL,
                      `business_line_code` VARCHAR(128) NULL,
                      `environment_code` VARCHAR(64) NULL,
                      `dedupe_key` VARCHAR(255) NOT NULL,
                      `read_flag` TINYINT(1) NOT NULL DEFAULT 0,
                      `read_at` DATETIME NULL,
                      `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (`id`),
                      UNIQUE KEY `uk_sys_notification_dedupe` (`recipient_user_name`, `dedupe_key`),
                      KEY `idx_sys_notification_recipient` (`recipient_user_name`, `read_flag`, `created_at`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            log.warn("Created missing table {}", TABLE_NAME);
        }
    }

    private boolean tableExists(Connection connection) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        return tableExists(metaData, connection.getCatalog(), TABLE_NAME)
                || tableExists(metaData, connection.getCatalog(), TABLE_NAME.toUpperCase(Locale.ROOT))
                || tableExists(metaData, connection.getCatalog(), TABLE_NAME.toLowerCase(Locale.ROOT));
    }

    private boolean tableExists(DatabaseMetaData metaData, String catalog, String tableName) throws SQLException {
        try (ResultSet resultSet = metaData.getTables(catalog, null, tableName, new String[]{"TABLE"})) {
            return resultSet.next();
        }
    }
}
