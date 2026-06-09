package cn.aslight.workhub.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/**
 * 项目表兼容补丁，负责为已有库补齐新增字段。
 */
@Component
public class ProjectSchemaPatchRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProjectSchemaPatchRunner.class);
    private static final String TABLE_NAME = "pm_project";
    private static final String BUSINESS_LINE_COLUMN = "business_line";
    private static final String OLD_PROJECT_GROUP_COLUMN = "project_group";
    private static final String BUSINESS_LINE_TABLE_NAME = "pm_business_line";
    private static final String OLD_PROJECT_GROUP_TABLE_NAME = "pm_project_group";
    private static final String BUSINESS_LINE_MEMBER_TABLE_NAME = "pm_business_line_member";
    private static final String OLD_PROJECT_GROUP_MEMBER_TABLE_NAME = "pm_project_group_member";

    private final DataSource dataSource;

    public ProjectSchemaPatchRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            migrateBusinessLineTable(connection);
            migrateBusinessLineMemberTable(connection);
            ensureBusinessLineColumn(connection);
            ensureBusinessLineColumn(connection, "pm_project_involved_system", "VARCHAR(128) NOT NULL DEFAULT ''");
            migrateProjectInvolvedSystemScope(connection);
            ensureBusinessLineColumn(connection, "pm_intake_development_analysis", "VARCHAR(128) NULL");
            ensureBusinessLineColumn(connection, "pm_intake_clarification_analysis", "VARCHAR(128) NULL");
            dropLegacyProjectBusinessLineColumns(connection);
        }
    }

    private void ensureBusinessLineColumn(Connection connection) throws SQLException {
        if (!tableExists(connection, TABLE_NAME)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            if (columnExists(connection, TABLE_NAME, OLD_PROJECT_GROUP_COLUMN) && !columnExists(connection, TABLE_NAME, BUSINESS_LINE_COLUMN)) {
                statement.execute("ALTER TABLE `" + TABLE_NAME + "` CHANGE COLUMN `" + OLD_PROJECT_GROUP_COLUMN + "` `" + BUSINESS_LINE_COLUMN + "` VARCHAR(128) NOT NULL DEFAULT ''");
                log.warn("Renamed column {} to {} on {}", OLD_PROJECT_GROUP_COLUMN, BUSINESS_LINE_COLUMN, TABLE_NAME);
            } else if (!columnExists(connection, TABLE_NAME, BUSINESS_LINE_COLUMN)) {
                statement.execute("ALTER TABLE `" + TABLE_NAME + "` ADD COLUMN `" + BUSINESS_LINE_COLUMN + "` VARCHAR(128) NOT NULL DEFAULT '' AFTER `project_type`");
                log.warn("Added missing column {} to {}", BUSINESS_LINE_COLUMN, TABLE_NAME);
            } else if (columnExists(connection, TABLE_NAME, OLD_PROJECT_GROUP_COLUMN)) {
                statement.execute("UPDATE `" + TABLE_NAME + "` SET `" + BUSINESS_LINE_COLUMN + "` = `" + OLD_PROJECT_GROUP_COLUMN + "` WHERE `" + BUSINESS_LINE_COLUMN + "` = '' AND `" + OLD_PROJECT_GROUP_COLUMN + "` <> ''");
                statement.execute("ALTER TABLE `" + TABLE_NAME + "` DROP COLUMN `" + OLD_PROJECT_GROUP_COLUMN + "`");
                log.warn("Dropped legacy column {} from {}", OLD_PROJECT_GROUP_COLUMN, TABLE_NAME);
            }
        }
    }

    private void migrateBusinessLineTable(Connection connection) throws SQLException {
        if (!tableExists(connection, BUSINESS_LINE_TABLE_NAME) && tableExists(connection, OLD_PROJECT_GROUP_TABLE_NAME)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("RENAME TABLE `" + OLD_PROJECT_GROUP_TABLE_NAME + "` TO `" + BUSINESS_LINE_TABLE_NAME + "`");
                log.warn("Renamed table {} to {}", OLD_PROJECT_GROUP_TABLE_NAME, BUSINESS_LINE_TABLE_NAME);
            }
        }
        if (tableExists(connection, BUSINESS_LINE_TABLE_NAME)) {
            try (Statement statement = connection.createStatement()) {
                if (columnExists(connection, BUSINESS_LINE_TABLE_NAME, "group_name")
                        && !columnExists(connection, BUSINESS_LINE_TABLE_NAME, "business_line_name")) {
                    statement.execute("ALTER TABLE `" + BUSINESS_LINE_TABLE_NAME + "` CHANGE COLUMN `group_name` `business_line_name` VARCHAR(128) NOT NULL");
                    log.warn("Renamed group_name to business_line_name on {}", BUSINESS_LINE_TABLE_NAME);
                }
            }
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE `pm_business_line` (
                      `id` BIGINT NOT NULL AUTO_INCREMENT,
                      `business_line_name` VARCHAR(128) NOT NULL,
                      `gitlab_group_name` VARCHAR(255) NULL,
                      `description` TEXT NULL,
                      `enabled` TINYINT(1) NOT NULL DEFAULT 1,
                      `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      PRIMARY KEY (`id`),
                      UNIQUE KEY `uk_pm_business_line_name` (`business_line_name`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            log.warn("Created missing table {}", BUSINESS_LINE_TABLE_NAME);
            if (tableExists(connection, TABLE_NAME)) {
                statement.execute("""
                        INSERT IGNORE INTO `pm_business_line` (`business_line_name`, `enabled`)
                        SELECT DISTINCT `business_line`, 1
                        FROM `pm_project`
                        WHERE `business_line` IS NOT NULL
                          AND `business_line` <> ''
                        """);
            }
        }
    }

    private void migrateBusinessLineMemberTable(Connection connection) throws SQLException {
        if (!tableExists(connection, BUSINESS_LINE_MEMBER_TABLE_NAME) && tableExists(connection, OLD_PROJECT_GROUP_MEMBER_TABLE_NAME)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("RENAME TABLE `" + OLD_PROJECT_GROUP_MEMBER_TABLE_NAME + "` TO `" + BUSINESS_LINE_MEMBER_TABLE_NAME + "`");
                log.warn("Renamed table {} to {}", OLD_PROJECT_GROUP_MEMBER_TABLE_NAME, BUSINESS_LINE_MEMBER_TABLE_NAME);
            }
        }
        ensureBusinessLineColumn(connection, BUSINESS_LINE_MEMBER_TABLE_NAME, "VARCHAR(128) NOT NULL");
    }

    private void ensureBusinessLineColumn(Connection connection, String tableName, String definition) throws SQLException {
        if (!tableExists(connection, tableName)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            if (columnExists(connection, tableName, OLD_PROJECT_GROUP_COLUMN) && !columnExists(connection, tableName, BUSINESS_LINE_COLUMN)) {
                statement.execute("ALTER TABLE `" + tableName + "` CHANGE COLUMN `" + OLD_PROJECT_GROUP_COLUMN + "` `" + BUSINESS_LINE_COLUMN + "` " + definition);
                log.warn("Renamed column {} to {} on {}", OLD_PROJECT_GROUP_COLUMN, BUSINESS_LINE_COLUMN, tableName);
            } else if (columnExists(connection, tableName, OLD_PROJECT_GROUP_COLUMN)) {
                statement.execute("UPDATE `" + tableName + "` SET `" + BUSINESS_LINE_COLUMN + "` = `" + OLD_PROJECT_GROUP_COLUMN + "` WHERE (`" + BUSINESS_LINE_COLUMN + "` IS NULL OR `" + BUSINESS_LINE_COLUMN + "` = '') AND `" + OLD_PROJECT_GROUP_COLUMN + "` IS NOT NULL");
                statement.execute("ALTER TABLE `" + tableName + "` DROP COLUMN `" + OLD_PROJECT_GROUP_COLUMN + "`");
                log.warn("Dropped legacy column {} from {}", OLD_PROJECT_GROUP_COLUMN, tableName);
            }
        }
    }

    private void migrateProjectInvolvedSystemScope(Connection connection) throws SQLException {
        String tableName = "pm_project_involved_system";
        if (!tableExists(connection, tableName) || !columnExists(connection, tableName, "system_scope")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("UPDATE `" + tableName + "` SET `system_scope` = 'BUSINESS_LINE' WHERE `system_scope` = 'PROJECT_GROUP'");
            log.warn("Migrated legacy PROJECT_GROUP system scope to BUSINESS_LINE on {}", tableName);
        }
    }

    private void dropLegacyProjectBusinessLineColumns(Connection connection) throws SQLException {
        if (!tableExists(connection, TABLE_NAME)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            if (columnExists(connection, TABLE_NAME, "business_line_code")) {
                statement.execute("ALTER TABLE `" + TABLE_NAME + "` DROP COLUMN `business_line_code`");
                log.warn("Dropped legacy column business_line_code from {}", TABLE_NAME);
            }
            if (columnExists(connection, TABLE_NAME, "business_line_name")) {
                statement.execute("ALTER TABLE `" + TABLE_NAME + "` DROP COLUMN `business_line_name`");
                log.warn("Dropped legacy column business_line_name from {}", TABLE_NAME);
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
