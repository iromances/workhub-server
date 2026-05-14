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
    private static final String BUSINESS_LINE_CODE_COLUMN = "business_line_code";
    private static final String BUSINESS_LINE_NAME_COLUMN = "business_line_name";
    private static final String PROJECT_GROUP_COLUMN = "project_group";
    private static final String PROJECT_GROUP_TABLE_NAME = "pm_project_group";

    private final DataSource dataSource;

    public ProjectSchemaPatchRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ensureBusinessLineColumns(connection);
            ensureProjectGroupColumn(connection);
            ensureProjectGroupTable(connection);
        }
    }

    private void ensureBusinessLineColumns(Connection connection) throws SQLException {
        if (!tableExists(connection, TABLE_NAME)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            if (!columnExists(connection, TABLE_NAME, BUSINESS_LINE_CODE_COLUMN)) {
                statement.execute("ALTER TABLE `" + TABLE_NAME + "` ADD COLUMN `" + BUSINESS_LINE_CODE_COLUMN + "` VARCHAR(64) NOT NULL DEFAULT '' AFTER `id`");
                log.warn("Added missing column {} to {}", BUSINESS_LINE_CODE_COLUMN, TABLE_NAME);
            }
            if (!columnExists(connection, TABLE_NAME, BUSINESS_LINE_NAME_COLUMN)) {
                statement.execute("ALTER TABLE `" + TABLE_NAME + "` ADD COLUMN `" + BUSINESS_LINE_NAME_COLUMN + "` VARCHAR(128) NOT NULL DEFAULT '' AFTER `" + BUSINESS_LINE_CODE_COLUMN + "`");
                log.warn("Added missing column {} to {}", BUSINESS_LINE_NAME_COLUMN, TABLE_NAME);
            }
        }
    }

    private void ensureProjectGroupColumn(Connection connection) throws SQLException {
        if (!tableExists(connection, TABLE_NAME) || columnExists(connection, TABLE_NAME, PROJECT_GROUP_COLUMN)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE `" + TABLE_NAME + "` ADD COLUMN `" + PROJECT_GROUP_COLUMN + "` VARCHAR(128) NOT NULL DEFAULT '' AFTER `project_type`");
            log.warn("Added missing column {} to {}", PROJECT_GROUP_COLUMN, TABLE_NAME);
        }
    }

    private void ensureProjectGroupTable(Connection connection) throws SQLException {
        if (tableExists(connection, PROJECT_GROUP_TABLE_NAME)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE `pm_project_group` (
                      `id` BIGINT NOT NULL AUTO_INCREMENT,
                      `group_name` VARCHAR(128) NOT NULL,
                      `gitlab_group_name` VARCHAR(255) NULL,
                      `description` TEXT NULL,
                      `enabled` TINYINT(1) NOT NULL DEFAULT 1,
                      `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      PRIMARY KEY (`id`),
                      UNIQUE KEY `uk_pm_project_group_name` (`group_name`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            log.warn("Created missing table {}", PROJECT_GROUP_TABLE_NAME);
            if (tableExists(connection, TABLE_NAME)) {
                statement.execute("""
                        INSERT IGNORE INTO `pm_project_group` (`group_name`, `enabled`)
                        SELECT DISTINCT `project_group`, 1
                        FROM `pm_project`
                        WHERE `project_group` IS NOT NULL
                          AND `project_group` <> ''
                        """);
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
