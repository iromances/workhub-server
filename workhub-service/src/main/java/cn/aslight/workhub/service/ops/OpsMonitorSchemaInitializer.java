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
public class OpsMonitorSchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(OpsMonitorSchemaInitializer.class);
    private static final String TABLE_NAME = "ops_monitor_config";

    private final DataSource dataSource;
    private volatile boolean initialized;

    public OpsMonitorSchemaInitializer(DataSource dataSource) {
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
                throw new IllegalStateException("初始化运维监测表结构失败：" + ex.getMessage(), ex);
            }
        }
    }

    private void ensureTable(Connection connection) throws SQLException {
        if (tableExists(connection)) {
            ensureColumn(connection, "xxl_job_database_name",
                    "ALTER TABLE `ops_monitor_config` ADD COLUMN `xxl_job_database_name` VARCHAR(128) NULL AFTER `password_encrypted`");
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE `ops_monitor_config` (
                      `id` BIGINT NOT NULL AUTO_INCREMENT,
                      `monitor_type` VARCHAR(32) NOT NULL,
                      `monitor_key` VARCHAR(128) NOT NULL,
                      `business_line_code` VARCHAR(128) NOT NULL,
                      `environment_code` VARCHAR(64) NOT NULL,
                      `name` VARCHAR(128) NOT NULL,
                      `admin_base_url` VARCHAR(512) NULL,
                      `username` VARCHAR(128) NULL,
                      `password_encrypted` TEXT NULL,
                      `xxl_job_database_name` VARCHAR(128) NULL,
                      `executor_app_name` VARCHAR(128) NULL,
                      `job_handler` VARCHAR(255) NULL,
                      `job_desc` VARCHAR(255) NULL,
                      `mq_topic` VARCHAR(255) NULL,
                      `mq_consumer_group` VARCHAR(255) NULL,
                      `mq_lag_threshold` INT NULL,
                      `enabled` TINYINT(1) NOT NULL DEFAULT 1,
                      `last_status` VARCHAR(32) NULL,
                      `last_message` VARCHAR(1000) NULL,
                      `last_checked_at` DATETIME NULL,
                      `remark` VARCHAR(255) NULL,
                      `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      PRIMARY KEY (`id`),
                      UNIQUE KEY `uk_ops_monitor_config_key` (`monitor_key`),
                      KEY `idx_ops_monitor_config_list` (`business_line_code`, `environment_code`, `monitor_type`, `enabled`)
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

    private void ensureColumn(Connection connection, String columnName, String alterSql) throws SQLException {
        if (columnExists(connection, columnName)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(alterSql);
            log.warn("Added missing column {} to {}", columnName, TABLE_NAME);
        }
    }

    private boolean columnExists(Connection connection, String columnName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        return columnExists(metaData, connection.getCatalog(), TABLE_NAME, columnName)
                || columnExists(metaData, connection.getCatalog(), TABLE_NAME.toUpperCase(Locale.ROOT), columnName.toUpperCase(Locale.ROOT))
                || columnExists(metaData, connection.getCatalog(), TABLE_NAME.toLowerCase(Locale.ROOT), columnName.toLowerCase(Locale.ROOT));
    }

    private boolean columnExists(DatabaseMetaData metaData, String catalog, String tableName, String columnName) throws SQLException {
        try (ResultSet resultSet = metaData.getColumns(catalog, null, tableName, columnName)) {
            return resultSet.next();
        }
    }
}
