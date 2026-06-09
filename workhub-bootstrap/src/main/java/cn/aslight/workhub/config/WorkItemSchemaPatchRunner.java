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
 * 工作项表兼容补丁，负责为已有库补齐新增字段。
 */
@Component
public class WorkItemSchemaPatchRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WorkItemSchemaPatchRunner.class);
    private static final String TABLE_NAME = "pm_work_item";

    private final DataSource dataSource;

    public WorkItemSchemaPatchRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            if (!tableExists(connection, TABLE_NAME)) {
                return;
            }
            ensureColumn(connection, "pause_previous_status", "VARCHAR(32) NULL AFTER `finished_at`");
            ensureColumn(connection, "pause_reason", "VARCHAR(255) NULL AFTER `pause_previous_status`");
            ensureColumn(connection, "pause_date", "DATE NULL AFTER `pause_reason`");
        }
    }

    private void ensureColumn(Connection connection, String columnName, String definition) throws SQLException {
        if (columnExists(connection, TABLE_NAME, columnName)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE `" + TABLE_NAME + "` ADD COLUMN `" + columnName + "` " + definition);
            log.warn("Added missing column {} to {}", columnName, TABLE_NAME);
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
