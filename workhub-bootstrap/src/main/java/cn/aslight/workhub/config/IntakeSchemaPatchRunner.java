package cn.aslight.workhub.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;

@Component
@Order(0)
/**
 * IntakeSchemaPatchRunner 模型。
 */
public class IntakeSchemaPatchRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IntakeSchemaPatchRunner.class);

    private static final String TABLE_NAME = "pm_intake_record";
    private static final String HISTORY_TABLE_NAME = "pm_intake_history";
    private static final List<ColumnPatch> COLUMN_PATCHES = List.of(
            new ColumnPatch("structured_data_json", "MEDIUMTEXT NULL"),
            new ColumnPatch("demand_status", "VARCHAR(32) NULL"),
            new ColumnPatch("enrichment_status", "VARCHAR(16) NULL"),
            new ColumnPatch("enrichment_error_summary", "VARCHAR(255) NULL"),
            new ColumnPatch("enrichment_updated_at", "DATETIME NULL"),
            new ColumnPatch("development_owner_user_name", "VARCHAR(64) NULL"),
            new ColumnPatch("deleted", "TINYINT(1) NOT NULL DEFAULT 0"),
            new ColumnPatch("deleted_at", "DATETIME NULL"),
            new ColumnPatch("deleted_by", "VARCHAR(64) NULL")
    );

    private final DataSource dataSource;

    public IntakeSchemaPatchRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            if (!tableExists(connection, TABLE_NAME)) {
                log.info("Skipped intake schema patch because table {} does not exist", TABLE_NAME);
                return;
            }

            for (ColumnPatch patch : COLUMN_PATCHES) {
                ensureColumn(connection, TABLE_NAME, patch);
            }
            ensureUtf8mb4(connection, TABLE_NAME);
            ensureMediumText(connection, TABLE_NAME, "structured_data_json");
            ensureMediumText(connection, TABLE_NAME, "ai_draft_json");
            ensureExternalMessageUniqueIndex(connection);
            ensureHistoryTable(connection);
        }
    }

    void ensureColumn(Connection connection, String tableName, ColumnPatch patch) throws SQLException {
        if (columnExists(connection, tableName, patch.name())) {
            return;
        }
        executeAlter(
                connection,
                "ALTER TABLE `" + tableName + "` ADD COLUMN `" + patch.name() + "` " + patch.definition(),
                "Added missing column {} to {}",
                patch.name(),
                tableName
        );
    }

    void ensureUtf8mb4(Connection connection, String tableName) throws SQLException {
        String sql = """
                SELECT 1
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND CHARACTER_SET_NAME IS NOT NULL
                  AND CHARACTER_SET_NAME <> 'utf8mb4'
                LIMIT 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return;
                }
            }
        }
        executeAlter(
                connection,
                "ALTER TABLE `" + tableName + "` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci",
                "Converted table {} to utf8mb4",
                tableName,
                null
        );
    }

    void ensureMediumText(Connection connection, String tableName, String columnName) throws SQLException {
        String sql = """
                SELECT DATA_TYPE
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next() || "mediumtext".equalsIgnoreCase(resultSet.getString("DATA_TYPE"))) {
                    return;
                }
            }
        }
        executeAlter(
                connection,
                "ALTER TABLE `" + tableName + "` MODIFY COLUMN `" + columnName + "` MEDIUMTEXT NULL",
                "Changed column {} on {} to MEDIUMTEXT",
                columnName,
                tableName
        );
    }

    void ensureExternalMessageUniqueIndex(Connection connection) throws SQLException {
        String indexName = "uk_pm_intake_record_external_message_id";
        if (indexExists(connection, TABLE_NAME, indexName)) {
            return;
        }
        executeAlter(
                connection,
                "ALTER TABLE `" + TABLE_NAME + "` ADD UNIQUE KEY `" + indexName + "` (`external_message_id`)",
                "Added missing unique index {} to {}",
                indexName,
                TABLE_NAME
        );
    }

    void ensureHistoryTable(Connection connection) throws SQLException {
        if (tableExists(connection, HISTORY_TABLE_NAME)) {
            return;
        }
        executeAlter(
                connection,
                """
                        CREATE TABLE `pm_intake_history` (
                          `id` BIGINT NOT NULL AUTO_INCREMENT,
                          `intake_id` BIGINT NOT NULL,
                          `action_type` VARCHAR(32) NOT NULL,
                          `action_summary` VARCHAR(128) NOT NULL,
                          `detail_text` TEXT NULL,
                          `operator_user_name` VARCHAR(64) NOT NULL,
                          `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          PRIMARY KEY (`id`),
                          KEY `idx_pm_intake_history_intake_id` (`intake_id`)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """,
                "Created missing table {}",
                HISTORY_TABLE_NAME,
                null
        );
    }

    private void executeAlter(Connection connection,
                              String sql,
                              String successLogPattern,
                              Object arg1,
                              Object arg2) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
            log.warn(successLogPattern, arg1, arg2);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to auto patch intake schema with SQL: " + sql, ex);
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

    boolean indexExists(Connection connection, String tableName, String indexName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getIndexInfo(connection.getCatalog(), null, tableName, true, false)) {
            while (resultSet.next()) {
                String currentIndexName = resultSet.getString("INDEX_NAME");
                if (indexName.equalsIgnoreCase(currentIndexName)) {
                    return true;
                }
            }
        }
        try (ResultSet resultSet = metaData.getIndexInfo(connection.getCatalog(), null, tableName.toUpperCase(Locale.ROOT), true, false)) {
            while (resultSet.next()) {
                String currentIndexName = resultSet.getString("INDEX_NAME");
                if (indexName.equalsIgnoreCase(currentIndexName)) {
                    return true;
                }
            }
        }
        try (ResultSet resultSet = metaData.getIndexInfo(connection.getCatalog(), null, tableName.toLowerCase(Locale.ROOT), true, false)) {
            while (resultSet.next()) {
                String currentIndexName = resultSet.getString("INDEX_NAME");
                if (indexName.equalsIgnoreCase(currentIndexName)) {
                    return true;
                }
            }
        }
        return false;
    }

    record ColumnPatch(String name, String definition) {
    }
}
