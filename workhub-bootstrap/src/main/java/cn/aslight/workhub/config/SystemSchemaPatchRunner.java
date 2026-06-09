package cn.aslight.workhub.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/**
 * 系统配置与研发分析相关表结构兼容补丁。
 */
@Component
public class SystemSchemaPatchRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SystemSchemaPatchRunner.class);

    private final DataSource dataSource;

    public SystemSchemaPatchRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ensureTable(connection, "sys_config_item", """
                    CREATE TABLE `sys_config_item` (
                      `id` BIGINT NOT NULL AUTO_INCREMENT,
                      `config_group` VARCHAR(128) NOT NULL,
                      `config_key` VARCHAR(128) NOT NULL,
                      `config_name` VARCHAR(128) NOT NULL,
                      `value_type` VARCHAR(32) NOT NULL,
                      `plain_value` TEXT NULL,
                      `encrypted_value` TEXT NULL,
                      `masked_value` VARCHAR(255) NULL,
                      `enabled` TINYINT(1) NOT NULL DEFAULT 1,
                      `remark` VARCHAR(255) NULL,
                      `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      PRIMARY KEY (`id`),
                      UNIQUE KEY `uk_sys_config_group_key` (`config_group`, `config_key`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            ensureTable(connection, "pm_developer_resource", """
                    CREATE TABLE `pm_developer_resource` (
                      `id` BIGINT NOT NULL AUTO_INCREMENT,
                      `user_name` VARCHAR(64) NOT NULL,
                      `display_name` VARCHAR(128) NOT NULL,
                      `enabled` TINYINT(1) NOT NULL DEFAULT 1,
                      `remark` VARCHAR(255) NULL,
                      `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      PRIMARY KEY (`id`),
                      UNIQUE KEY `uk_pm_developer_resource_user_name` (`user_name`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            ensureTable(connection, "mcp_resource_config", """
                    CREATE TABLE `mcp_resource_config` (
                      `id` BIGINT NOT NULL AUTO_INCREMENT,
                      `resource_type` VARCHAR(32) NOT NULL,
                      `target_key` VARCHAR(128) NOT NULL,
                      `business_line_code` VARCHAR(128) NOT NULL,
                      `environment_code` VARCHAR(64) NOT NULL,
                      `name` VARCHAR(128) NOT NULL,
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
            ensureColumn(connection, "mcp_resource_config", "password_encrypted",
                    "ALTER TABLE `mcp_resource_config` ADD COLUMN `password_encrypted` TEXT NULL AFTER `secret_ref`");
            ensureColumn(connection, "mcp_resource_config", "ssh_password_encrypted",
                    "ALTER TABLE `mcp_resource_config` ADD COLUMN `ssh_password_encrypted` TEXT NULL AFTER `password_encrypted`");
            ensureColumn(connection, "mcp_resource_config", "ssh_bastion_enabled",
                    "ALTER TABLE `mcp_resource_config` ADD COLUMN `ssh_bastion_enabled` TINYINT(1) NOT NULL DEFAULT 0 AFTER `ssh_password_encrypted`");
            ensureColumn(connection, "mcp_resource_config", "ssh_bastion_password_encrypted",
                    "ALTER TABLE `mcp_resource_config` ADD COLUMN `ssh_bastion_password_encrypted` TEXT NULL AFTER `ssh_bastion_user`");
            migrateMcpBastionEnabled(connection);
            ensureTable(connection, "mcp_resource_business_line", """
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
            migrateMcpBusinessLineBindings(connection);
            migrateDeveloperResources(connection);
            ensureTable(connection, "pm_business_line_member", """
                    CREATE TABLE `pm_business_line_member` (
                      `id` BIGINT NOT NULL AUTO_INCREMENT,
                      `business_line` VARCHAR(128) NOT NULL,
                      `member_user_name` VARCHAR(64) NOT NULL,
                      `member_display_name` VARCHAR(128) NOT NULL,
                      `enabled` TINYINT(1) NOT NULL DEFAULT 1,
                      `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      PRIMARY KEY (`id`),
                      UNIQUE KEY `uk_pm_business_line_member` (`business_line`, `member_user_name`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            ensureTable(connection, "pm_intake_development_analysis", """
                    CREATE TABLE `pm_intake_development_analysis` (
                      `id` BIGINT NOT NULL AUTO_INCREMENT,
                      `intake_id` BIGINT NOT NULL,
                      `project_id` BIGINT NULL,
                      `business_line` VARCHAR(128) NULL,
                      `repository_url` VARCHAR(512) NULL,
                      `analysis_status` VARCHAR(32) NOT NULL,
                      `analysis_message` VARCHAR(255) NULL,
                      `draft_json` TEXT NULL,
                      `zentao_sync_status` VARCHAR(32) NULL,
                      `zentao_sync_message` VARCHAR(255) NULL,
                      `created_by` VARCHAR(64) NOT NULL,
                      `updated_by` VARCHAR(64) NOT NULL,
                      `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      PRIMARY KEY (`id`),
                      KEY `idx_pm_intake_development_analysis_intake_id` (`intake_id`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            ensureTable(connection, "pm_intake_clarification_analysis", """
                    CREATE TABLE `pm_intake_clarification_analysis` (
                      `id` BIGINT NOT NULL AUTO_INCREMENT,
                      `intake_id` BIGINT NOT NULL,
                      `business_line` VARCHAR(128) NULL,
                      `analysis_status` VARCHAR(32) NOT NULL,
                      `analysis_message` VARCHAR(255) NULL,
                      `items_json` TEXT NULL,
                      `created_by` VARCHAR(64) NOT NULL,
                      `updated_by` VARCHAR(64) NOT NULL,
                      `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      PRIMARY KEY (`id`),
                      KEY `idx_pm_intake_clarification_analysis_intake_id` (`intake_id`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            ensureTable(connection, "pm_project_involved_system", """
                    CREATE TABLE `pm_project_involved_system` (
                      `id` BIGINT NOT NULL AUTO_INCREMENT,
                      `system_scope` VARCHAR(32) NOT NULL,
                      `business_line` VARCHAR(128) NOT NULL DEFAULT '',
                      `system_name` VARCHAR(128) NOT NULL,
                      `description` TEXT NULL,
                      `enabled` TINYINT(1) NOT NULL DEFAULT 1,
                      `sort_order` INT NOT NULL DEFAULT 0,
                      `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      PRIMARY KEY (`id`),
                      UNIQUE KEY `uk_pm_project_involved_system_name` (`system_scope`, `business_line`, `system_name`),
                      KEY `idx_pm_project_involved_system_select` (`system_scope`, `business_line`, `enabled`, `sort_order`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            ensureTable(connection, "pm_intake_work_item_relation", """
                    CREATE TABLE `pm_intake_work_item_relation` (
                      `id` BIGINT NOT NULL AUTO_INCREMENT,
                      `intake_id` BIGINT NOT NULL,
                      `work_item_id` BIGINT NOT NULL,
                      `draft_index` INT NULL,
                      `relation_type` VARCHAR(32) NOT NULL,
                      `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (`id`),
                      UNIQUE KEY `uk_pm_intake_work_item_relation` (`intake_id`, `work_item_id`),
                      KEY `idx_pm_intake_work_item_relation_intake` (`intake_id`, `draft_index`),
                      KEY `idx_pm_intake_work_item_relation_work_item` (`work_item_id`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            ensureColumn(connection, "pm_intake_development_analysis", "analysis_message",
                    "ALTER TABLE `pm_intake_development_analysis` ADD COLUMN `analysis_message` VARCHAR(255) NULL AFTER `analysis_status`");
            ensureDefaultConfig(
                    connection,
                    "knowledge.project",
                    "vaultPath",
                    "项目知识库地址",
                    "TEXT",
                    "/Users/aslight/Obsidian Vault/Company Obsidian Vault",
                    "AI 任务评估遇到不确定业务口径时可参考的本地 Obsidian Vault 路径"
            );
            ensureDefaultConfig(
                    connection,
                    "ai.codexCli",
                    "model",
                    "Codex CLI 模型",
                    "TEXT",
                    "gpt-5.5",
                    "AI 任务评估调用 Codex CLI 时使用的模型，优先级高于 application.yml"
            );
            ensureDefaultConfig(
                    connection,
                    "ai.codexCli",
                    "reasoningEffort",
                    "Codex CLI 推理强度",
                    "TEXT",
                    "xhigh",
                    "AI 任务评估调用 Codex CLI 时使用的推理强度，xhigh 表示最高"
            );
            ensureDefaultConfig(
                    connection,
                    "intake.requirementFolder",
                    "basePath",
                    "需求文件夹基础目录",
                    "TEXT",
                    "/Users/aslight/Desktop/进行中的需求",
                    "需求管理打开需求文件夹时使用的本地基础目录"
            );
        }
    }

    private void ensureTable(Connection connection, String tableName, String createSql) throws SQLException {
        if (tableExists(connection, tableName)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(createSql);
            log.warn("Created missing table {}", tableName);
        }
    }

    private void ensureColumn(Connection connection, String tableName, String columnName, String alterSql) throws SQLException {
        if (!tableExists(connection, tableName) || columnExists(connection, tableName, columnName)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(alterSql);
            log.warn("Added missing column {} to {}", columnName, tableName);
        }
    }

    private void ensureDefaultConfig(Connection connection,
                                     String configGroup,
                                     String configKey,
                                     String configName,
                                     String valueType,
                                     String plainValue,
                                     String remark) throws SQLException {
        if (configExists(connection, configGroup, configKey)) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO sys_config_item (
                    config_group, config_key, config_name, value_type, plain_value, enabled, remark
                ) VALUES (?, ?, ?, ?, ?, 1, ?)
                """)) {
            statement.setString(1, configGroup);
            statement.setString(2, configKey);
            statement.setString(3, configName);
            statement.setString(4, valueType);
            statement.setString(5, plainValue);
            statement.setString(6, remark);
            statement.executeUpdate();
            log.warn("Inserted default system config {}.{}", configGroup, configKey);
        }
    }

    private void migrateDeveloperResources(Connection connection) throws SQLException {
        if (!tableExists(connection, "sys_user") || !tableExists(connection, "pm_developer_resource")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            int inserted = statement.executeUpdate("""
                    INSERT INTO pm_developer_resource (
                        user_name, display_name, enabled
                    )
                    SELECT user_name, display_name, 1
                    FROM sys_user
                    WHERE status = 'ACTIVE'
                    ON DUPLICATE KEY UPDATE
                        display_name = IF(pm_developer_resource.display_name IS NULL OR pm_developer_resource.display_name = '', VALUES(display_name), pm_developer_resource.display_name),
                        enabled = pm_developer_resource.enabled
                    """);
            if (inserted > 0) {
                log.warn("Migrated {} developer resources from sys_user", inserted);
            }
        }
    }

    private void migrateMcpBusinessLineBindings(Connection connection) throws SQLException {
        if (!tableExists(connection, "mcp_resource_config") || !tableExists(connection, "mcp_resource_business_line")) {
            return;
        }
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

    private void migrateMcpBastionEnabled(Connection connection) throws SQLException {
        if (!tableExists(connection, "mcp_resource_config") || !columnExists(connection, "mcp_resource_config", "ssh_bastion_enabled")) {
            return;
        }
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

    private boolean configExists(Connection connection, String configGroup, String configKey) throws SQLException {
        if (!tableExists(connection, "sys_config_item")) {
            return false;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM sys_config_item WHERE config_group = ? AND config_key = ? LIMIT 1
                """)) {
            statement.setString(1, configGroup);
            statement.setString(2, configKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getTables(connection.getCatalog(), null, tableName, new String[]{"TABLE"})) {
            if (resultSet.next()) {
                return true;
            }
        }
        try (ResultSet resultSet = metaData.getTables(connection.getCatalog(), null, tableName.toUpperCase(Locale.ROOT), new String[]{"TABLE"})) {
            if (resultSet.next()) {
                return true;
            }
        }
        try (ResultSet resultSet = metaData.getTables(connection.getCatalog(), null, tableName.toLowerCase(Locale.ROOT), new String[]{"TABLE"})) {
            return resultSet.next();
        }
    }

    boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getColumns(connection.getCatalog(), null, tableName, columnName)) {
            if (resultSet.next()) {
                return true;
            }
        }
        try (ResultSet resultSet = metaData.getColumns(connection.getCatalog(), null, tableName.toUpperCase(Locale.ROOT), columnName.toUpperCase(Locale.ROOT))) {
            if (resultSet.next()) {
                return true;
            }
        }
        try (ResultSet resultSet = metaData.getColumns(connection.getCatalog(), null, tableName.toLowerCase(Locale.ROOT), columnName.toLowerCase(Locale.ROOT))) {
            return resultSet.next();
        }
    }
}
