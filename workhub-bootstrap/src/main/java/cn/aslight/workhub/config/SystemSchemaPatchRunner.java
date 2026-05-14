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
            ensureTable(connection, "pm_project_group_member", """
                    CREATE TABLE `pm_project_group_member` (
                      `id` BIGINT NOT NULL AUTO_INCREMENT,
                      `project_group` VARCHAR(128) NOT NULL,
                      `member_user_name` VARCHAR(64) NOT NULL,
                      `member_display_name` VARCHAR(128) NOT NULL,
                      `enabled` TINYINT(1) NOT NULL DEFAULT 1,
                      `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      PRIMARY KEY (`id`),
                      UNIQUE KEY `uk_pm_project_group_member` (`project_group`, `member_user_name`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            ensureTable(connection, "pm_intake_development_analysis", """
                    CREATE TABLE `pm_intake_development_analysis` (
                      `id` BIGINT NOT NULL AUTO_INCREMENT,
                      `intake_id` BIGINT NOT NULL,
                      `project_id` BIGINT NULL,
                      `project_group` VARCHAR(128) NULL,
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
