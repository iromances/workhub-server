package cn.aslight.workhub.service.mcp;

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

/**
 * MCP 资源表结构运行时兼容补丁。
 */
@Service
public class McpResourceSchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(McpResourceSchemaInitializer.class);
    private static final String TABLE_NAME = "mcp_resource_config";

    private final DataSource dataSource;
    private volatile boolean initialized;

    public McpResourceSchemaInitializer(DataSource dataSource) {
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
                ensureColumn(connection, "password_encrypted",
                        "ALTER TABLE `mcp_resource_config` ADD COLUMN `password_encrypted` TEXT NULL AFTER `secret_ref`");
                ensureColumn(connection, "ssh_password_encrypted",
                        "ALTER TABLE `mcp_resource_config` ADD COLUMN `ssh_password_encrypted` TEXT NULL AFTER `password_encrypted`");
                ensureColumn(connection, "ssh_bastion_enabled",
                        "ALTER TABLE `mcp_resource_config` ADD COLUMN `ssh_bastion_enabled` TINYINT(1) NOT NULL DEFAULT 0 AFTER `ssh_password_encrypted`");
                ensureColumn(connection, "ssh_bastion_password_encrypted",
                        "ALTER TABLE `mcp_resource_config` ADD COLUMN `ssh_bastion_password_encrypted` TEXT NULL AFTER `ssh_bastion_user`");
                ensureColumn(connection, "system_name",
                        "ALTER TABLE `mcp_resource_config` ADD COLUMN `system_name` VARCHAR(128) NULL AFTER `name`");
                migrateBastionEnabled(connection);
                ensureBusinessLineTable(connection);
                migrateBusinessLineBindings(connection);
                initialized = true;
            } catch (SQLException ex) {
                throw new IllegalStateException("初始化 MCP 资源表结构失败：" + ex.getMessage(), ex);
            }
        }
    }

    private void ensureTable(Connection connection) throws SQLException {
        if (tableExists(connection)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE `mcp_resource_config` (
                      `id` BIGINT NOT NULL AUTO_INCREMENT,
                      `resource_type` VARCHAR(32) NOT NULL,
                      `target_key` VARCHAR(128) NOT NULL,
                      `business_line_code` VARCHAR(128) NOT NULL,
                      `environment_code` VARCHAR(64) NOT NULL,
                      `name` VARCHAR(128) NOT NULL,
                      `system_name` VARCHAR(128) NULL,
                      `host` VARCHAR(255) NOT NULL,
                      `port` INT NOT NULL,
                      `database_schema` VARCHAR(128) NULL,
                      `username` VARCHAR(128) NULL,
                      `secret_ref` VARCHAR(255) NULL,
                      `password_encrypted` TEXT NULL,
                      `ssh_password_encrypted` TEXT NULL,
                      `ssh_bastion_enabled` TINYINT(1) NOT NULL DEFAULT 0,
                      `ssh_bastion_host` VARCHAR(255) NULL,
                      `ssh_bastion_port` INT NULL,
                      `ssh_bastion_user` VARCHAR(128) NULL,
                      `ssh_bastion_password_encrypted` TEXT NULL,
                      `ssh_identity_file` VARCHAR(512) NULL,
                      `allowed_services_json` TEXT NULL,
                      `allowed_log_paths_json` TEXT NULL,
                      `profiles_json` TEXT NULL,
                      `enabled` TINYINT(1) NOT NULL DEFAULT 1,
                      `remark` VARCHAR(255) NULL,
                      `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      PRIMARY KEY (`id`),
                      UNIQUE KEY `uk_mcp_resource_config_target_key` (`target_key`),
                      KEY `idx_mcp_resource_config_list` (`business_line_code`, `environment_code`, `resource_type`, `enabled`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            log.warn("Created missing table {}", TABLE_NAME);
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

    private void ensureBusinessLineTable(Connection connection) throws SQLException {
        if (tableExists(connection, "mcp_resource_business_line")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE `mcp_resource_business_line` (
                      `id` BIGINT NOT NULL AUTO_INCREMENT,
                      `resource_id` BIGINT NOT NULL,
                      `business_line_code` VARCHAR(128) NOT NULL,
                      `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (`id`),
                      UNIQUE KEY `uk_mcp_resource_business_line` (`resource_id`, `business_line_code`),
                      KEY `idx_mcp_resource_business_line_code` (`business_line_code`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            log.warn("Created missing table mcp_resource_business_line");
        }
    }

    private void migrateBusinessLineBindings(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT IGNORE INTO mcp_resource_business_line (
                        resource_id,
                        business_line_code
                    )
                    SELECT id, business_line_code
                    FROM mcp_resource_config
                    WHERE business_line_code IS NOT NULL
                      AND business_line_code <> ''
                    """);
        }
    }

    private void migrateBastionEnabled(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    UPDATE mcp_resource_config
                    SET ssh_bastion_enabled = 1
                    WHERE ssh_bastion_host IS NOT NULL
                      AND ssh_bastion_host <> ''
                      AND ssh_bastion_enabled = 0
                    """);
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

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        return tableExists(metaData, connection.getCatalog(), tableName)
                || tableExists(metaData, connection.getCatalog(), tableName.toUpperCase(Locale.ROOT))
                || tableExists(metaData, connection.getCatalog(), tableName.toLowerCase(Locale.ROOT));
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
