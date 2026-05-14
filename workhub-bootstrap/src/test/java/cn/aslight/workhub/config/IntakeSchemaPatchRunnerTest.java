package cn.aslight.workhub.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntakeSchemaPatchRunnerTest {

    @Test
    void run_shouldPatchMissingColumnsAndIndex() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);
        PreparedStatement charsetStatement = mock(PreparedStatement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.getCatalog()).thenReturn("workhub");
        when(connection.createStatement()).thenReturn(statement);
        when(connection.prepareStatement(org.mockito.ArgumentMatchers.anyString())).thenReturn(charsetStatement);
        ResultSet utf8mb4Ok = resultSet(false);
        ResultSet structuredDataIsMediumText = resultSetWithDataType("mediumtext");
        ResultSet aiDraftIsMediumText = resultSetWithDataType("mediumtext");
        when(charsetStatement.executeQuery()).thenReturn(utf8mb4Ok, structuredDataIsMediumText, aiDraftIsMediumText);

        ResultSet tableExists = resultSet(true);
        when(metaData.getTables("workhub", null, "pm_intake_record", new String[]{"TABLE"})).thenReturn(tableExists);

        ResultSet missingStructuredData = resultSet(false);
        ResultSet missingDemandStatus = resultSet(false);
        ResultSet missingEnrichmentStatus = resultSet(false);
        ResultSet missingEnrichmentErrorSummary = resultSet(false);
        ResultSet missingEnrichmentUpdatedAt = resultSet(false);
        ResultSet missingDevelopmentOwner = resultSet(false);
        ResultSet missingDeleted = resultSet(false);
        ResultSet missingDeletedAt = resultSet(false);
        ResultSet missingDeletedBy = resultSet(false);
        when(metaData.getColumns("workhub", null, "pm_intake_record", "structured_data_json")).thenReturn(missingStructuredData);
        when(metaData.getColumns("workhub", null, "pm_intake_record", "demand_status")).thenReturn(missingDemandStatus);
        when(metaData.getColumns("workhub", null, "pm_intake_record", "enrichment_status")).thenReturn(missingEnrichmentStatus);
        when(metaData.getColumns("workhub", null, "pm_intake_record", "enrichment_error_summary")).thenReturn(missingEnrichmentErrorSummary);
        when(metaData.getColumns("workhub", null, "pm_intake_record", "enrichment_updated_at")).thenReturn(missingEnrichmentUpdatedAt);
        when(metaData.getColumns("workhub", null, "pm_intake_record", "development_owner_user_name")).thenReturn(missingDevelopmentOwner);
        when(metaData.getColumns("workhub", null, "pm_intake_record", "deleted")).thenReturn(missingDeleted);
        when(metaData.getColumns("workhub", null, "pm_intake_record", "deleted_at")).thenReturn(missingDeletedAt);
        when(metaData.getColumns("workhub", null, "pm_intake_record", "deleted_by")).thenReturn(missingDeletedBy);
        ResultSet missingStructuredDataUpper = resultSet(false);
        ResultSet missingDemandStatusUpper = resultSet(false);
        ResultSet missingEnrichmentStatusUpper = resultSet(false);
        ResultSet missingEnrichmentErrorSummaryUpper = resultSet(false);
        ResultSet missingEnrichmentUpdatedAtUpper = resultSet(false);
        ResultSet missingDevelopmentOwnerUpper = resultSet(false);
        ResultSet missingDeletedUpper = resultSet(false);
        ResultSet missingDeletedAtUpper = resultSet(false);
        ResultSet missingDeletedByUpper = resultSet(false);
        when(metaData.getColumns("workhub", null, "PM_INTAKE_RECORD", "STRUCTURED_DATA_JSON")).thenReturn(missingStructuredDataUpper);
        when(metaData.getColumns("workhub", null, "PM_INTAKE_RECORD", "DEMAND_STATUS")).thenReturn(missingDemandStatusUpper);
        when(metaData.getColumns("workhub", null, "PM_INTAKE_RECORD", "ENRICHMENT_STATUS")).thenReturn(missingEnrichmentStatusUpper);
        when(metaData.getColumns("workhub", null, "PM_INTAKE_RECORD", "ENRICHMENT_ERROR_SUMMARY")).thenReturn(missingEnrichmentErrorSummaryUpper);
        when(metaData.getColumns("workhub", null, "PM_INTAKE_RECORD", "ENRICHMENT_UPDATED_AT")).thenReturn(missingEnrichmentUpdatedAtUpper);
        when(metaData.getColumns("workhub", null, "PM_INTAKE_RECORD", "DEVELOPMENT_OWNER_USER_NAME")).thenReturn(missingDevelopmentOwnerUpper);
        when(metaData.getColumns("workhub", null, "PM_INTAKE_RECORD", "DELETED")).thenReturn(missingDeletedUpper);
        when(metaData.getColumns("workhub", null, "PM_INTAKE_RECORD", "DELETED_AT")).thenReturn(missingDeletedAtUpper);
        when(metaData.getColumns("workhub", null, "PM_INTAKE_RECORD", "DELETED_BY")).thenReturn(missingDeletedByUpper);

        ResultSet missingIndex = resultSet(false);
        ResultSet missingIndexUpper = resultSet(false);
        when(metaData.getIndexInfo("workhub", null, "pm_intake_record", true, false)).thenReturn(missingIndex);
        when(metaData.getIndexInfo("workhub", null, "PM_INTAKE_RECORD", true, false)).thenReturn(missingIndexUpper);
        ResultSet historyMissing = resultSet(false);
        ResultSet historyMissingUpper = resultSet(false);
        ResultSet historyMissingLower = resultSet(false);
        when(metaData.getTables("workhub", null, "pm_intake_history", new String[]{"TABLE"})).thenReturn(historyMissing);
        when(metaData.getTables("workhub", null, "PM_INTAKE_HISTORY", new String[]{"TABLE"})).thenReturn(historyMissingUpper);
        when(metaData.getTables("workhub", null, "pm_intake_history", new String[]{"TABLE"})).thenReturn(historyMissingLower);

        IntakeSchemaPatchRunner runner = new IntakeSchemaPatchRunner(dataSource);
        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(statement).execute("ALTER TABLE `pm_intake_record` ADD COLUMN `structured_data_json` MEDIUMTEXT NULL");
        verify(statement).execute("ALTER TABLE `pm_intake_record` ADD COLUMN `demand_status` VARCHAR(32) NULL");
        verify(statement).execute("ALTER TABLE `pm_intake_record` ADD COLUMN `enrichment_status` VARCHAR(16) NULL");
        verify(statement).execute("ALTER TABLE `pm_intake_record` ADD COLUMN `enrichment_error_summary` VARCHAR(255) NULL");
        verify(statement).execute("ALTER TABLE `pm_intake_record` ADD COLUMN `enrichment_updated_at` DATETIME NULL");
        verify(statement).execute("ALTER TABLE `pm_intake_record` ADD COLUMN `development_owner_user_name` VARCHAR(64) NULL");
        verify(statement).execute("ALTER TABLE `pm_intake_record` ADD COLUMN `deleted` TINYINT(1) NOT NULL DEFAULT 0");
        verify(statement).execute("ALTER TABLE `pm_intake_record` ADD COLUMN `deleted_at` DATETIME NULL");
        verify(statement).execute("ALTER TABLE `pm_intake_record` ADD COLUMN `deleted_by` VARCHAR(64) NULL");
        verify(statement).execute("ALTER TABLE `pm_intake_record` ADD UNIQUE KEY `uk_pm_intake_record_external_message_id` (`external_message_id`)");
        verify(statement).execute("""
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
                        """);
    }

    @Test
    void run_shouldSkipWhenColumnsAndIndexAlreadyExist() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);
        PreparedStatement charsetStatement = mock(PreparedStatement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.getCatalog()).thenReturn("workhub");
        when(connection.createStatement()).thenReturn(statement);
        when(connection.prepareStatement(org.mockito.ArgumentMatchers.anyString())).thenReturn(charsetStatement);
        ResultSet utf8mb4Ok = resultSet(false);
        ResultSet structuredDataIsMediumText = resultSetWithDataType("mediumtext");
        ResultSet aiDraftIsMediumText = resultSetWithDataType("mediumtext");
        when(charsetStatement.executeQuery()).thenReturn(utf8mb4Ok, structuredDataIsMediumText, aiDraftIsMediumText);

        ResultSet tableExists = resultSet(true);
        when(metaData.getTables("workhub", null, "pm_intake_record", new String[]{"TABLE"})).thenReturn(tableExists);

        ResultSet existingStructuredData = resultSet(true);
        ResultSet existingDemandStatus = resultSet(true);
        ResultSet existingEnrichmentStatus = resultSet(true);
        ResultSet existingEnrichmentErrorSummary = resultSet(true);
        ResultSet existingEnrichmentUpdatedAt = resultSet(true);
        ResultSet existingDevelopmentOwner = resultSet(true);
        ResultSet existingDeleted = resultSet(true);
        ResultSet existingDeletedAt = resultSet(true);
        ResultSet existingDeletedBy = resultSet(true);
        when(metaData.getColumns("workhub", null, "pm_intake_record", "structured_data_json")).thenReturn(existingStructuredData);
        when(metaData.getColumns("workhub", null, "pm_intake_record", "demand_status")).thenReturn(existingDemandStatus);
        when(metaData.getColumns("workhub", null, "pm_intake_record", "enrichment_status")).thenReturn(existingEnrichmentStatus);
        when(metaData.getColumns("workhub", null, "pm_intake_record", "enrichment_error_summary")).thenReturn(existingEnrichmentErrorSummary);
        when(metaData.getColumns("workhub", null, "pm_intake_record", "enrichment_updated_at")).thenReturn(existingEnrichmentUpdatedAt);
        when(metaData.getColumns("workhub", null, "pm_intake_record", "development_owner_user_name")).thenReturn(existingDevelopmentOwner);
        when(metaData.getColumns("workhub", null, "pm_intake_record", "deleted")).thenReturn(existingDeleted);
        when(metaData.getColumns("workhub", null, "pm_intake_record", "deleted_at")).thenReturn(existingDeletedAt);
        when(metaData.getColumns("workhub", null, "pm_intake_record", "deleted_by")).thenReturn(existingDeletedBy);

        ResultSet existingIndex = resultSetWithIndex("uk_pm_intake_record_external_message_id");
        when(metaData.getIndexInfo("workhub", null, "pm_intake_record", true, false)).thenReturn(existingIndex);
        ResultSet historyExists = resultSet(true);
        when(metaData.getTables("workhub", null, "pm_intake_history", new String[]{"TABLE"})).thenReturn(historyExists);

        IntakeSchemaPatchRunner runner = new IntakeSchemaPatchRunner(dataSource);
        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(statement, never()).execute(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void run_shouldConvertIntakeTableToUtf8mb4WhenLegacyCharsetExists() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);
        PreparedStatement charsetStatement = mock(PreparedStatement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.getCatalog()).thenReturn("workhub");
        when(connection.createStatement()).thenReturn(statement);
        when(connection.prepareStatement(org.mockito.ArgumentMatchers.anyString())).thenReturn(charsetStatement);

        ResultSet tableExists = resultSet(true);
        when(metaData.getTables("workhub", null, "pm_intake_record", new String[]{"TABLE"})).thenReturn(tableExists);
        for (String column : List.of(
                "structured_data_json",
                "demand_status",
                "enrichment_status",
                "enrichment_error_summary",
                "enrichment_updated_at",
                "development_owner_user_name",
                "deleted",
                "deleted_at",
                "deleted_by"
        )) {
            ResultSet existingColumn = resultSet(true);
            when(metaData.getColumns("workhub", null, "pm_intake_record", column)).thenReturn(existingColumn);
        }
        ResultSet legacyCharsetExists = resultSet(true);
        ResultSet structuredDataIsMediumText = resultSetWithDataType("mediumtext");
        ResultSet aiDraftIsMediumText = resultSetWithDataType("mediumtext");
        when(charsetStatement.executeQuery()).thenReturn(legacyCharsetExists, structuredDataIsMediumText, aiDraftIsMediumText);
        ResultSet existingIndex = resultSetWithIndex("uk_pm_intake_record_external_message_id");
        when(metaData.getIndexInfo("workhub", null, "pm_intake_record", true, false)).thenReturn(existingIndex);
        ResultSet historyExists = resultSet(true);
        when(metaData.getTables("workhub", null, "pm_intake_history", new String[]{"TABLE"})).thenReturn(historyExists);

        IntakeSchemaPatchRunner runner = new IntakeSchemaPatchRunner(dataSource);
        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(statement).execute("ALTER TABLE `pm_intake_record` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
    }

    @Test
    void run_shouldUpgradeStructuredJsonTextColumnsToMediumText() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);
        PreparedStatement columnStatement = mock(PreparedStatement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.getCatalog()).thenReturn("workhub");
        when(connection.createStatement()).thenReturn(statement);
        when(connection.prepareStatement(org.mockito.ArgumentMatchers.anyString())).thenReturn(columnStatement);

        ResultSet tableExists = resultSet(true);
        when(metaData.getTables("workhub", null, "pm_intake_record", new String[]{"TABLE"})).thenReturn(tableExists);
        for (String column : List.of(
                "structured_data_json",
                "demand_status",
                "enrichment_status",
                "enrichment_error_summary",
                "enrichment_updated_at",
                "development_owner_user_name",
                "deleted",
                "deleted_at",
                "deleted_by"
        )) {
            ResultSet existingColumn = resultSet(true);
            when(metaData.getColumns("workhub", null, "pm_intake_record", column)).thenReturn(existingColumn);
        }
        ResultSet utf8mb4Ok = resultSet(false);
        ResultSet structuredDataIsText = resultSetWithDataType("text");
        ResultSet aiDraftIsText = resultSetWithDataType("text");
        when(columnStatement.executeQuery()).thenReturn(utf8mb4Ok, structuredDataIsText, aiDraftIsText);
        ResultSet existingIndex = resultSetWithIndex("uk_pm_intake_record_external_message_id");
        when(metaData.getIndexInfo("workhub", null, "pm_intake_record", true, false)).thenReturn(existingIndex);
        ResultSet historyExists = resultSet(true);
        when(metaData.getTables("workhub", null, "pm_intake_history", new String[]{"TABLE"})).thenReturn(historyExists);

        IntakeSchemaPatchRunner runner = new IntakeSchemaPatchRunner(dataSource);
        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(statement).execute("ALTER TABLE `pm_intake_record` MODIFY COLUMN `structured_data_json` MEDIUMTEXT NULL");
        verify(statement).execute("ALTER TABLE `pm_intake_record` MODIFY COLUMN `ai_draft_json` MEDIUMTEXT NULL");
    }

    private ResultSet resultSet(boolean firstNext) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(firstNext, false);
        return resultSet;
    }

    private ResultSet resultSetWithIndex(String indexName) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString("INDEX_NAME")).thenReturn(indexName);
        return resultSet;
    }

    private ResultSet resultSetWithDataType(String dataType) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString("DATA_TYPE")).thenReturn(dataType);
        return resultSet;
    }
}
