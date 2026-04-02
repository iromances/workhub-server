package cn.aslight.workhub.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

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

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.getCatalog()).thenReturn("workhub");
        when(connection.createStatement()).thenReturn(statement);

        ResultSet tableExists = resultSet(true);
        when(metaData.getTables("workhub", null, "pm_intake_record", new String[]{"TABLE"})).thenReturn(tableExists);

        ResultSet missingStructuredData = resultSet(false);
        ResultSet missingDemandStatus = resultSet(false);
        ResultSet missingEnrichmentStatus = resultSet(false);
        ResultSet missingEnrichmentErrorSummary = resultSet(false);
        ResultSet missingEnrichmentUpdatedAt = resultSet(false);
        when(metaData.getColumns("workhub", null, "pm_intake_record", "structured_data_json")).thenReturn(missingStructuredData);
        when(metaData.getColumns("workhub", null, "pm_intake_record", "demand_status")).thenReturn(missingDemandStatus);
        when(metaData.getColumns("workhub", null, "pm_intake_record", "enrichment_status")).thenReturn(missingEnrichmentStatus);
        when(metaData.getColumns("workhub", null, "pm_intake_record", "enrichment_error_summary")).thenReturn(missingEnrichmentErrorSummary);
        when(metaData.getColumns("workhub", null, "pm_intake_record", "enrichment_updated_at")).thenReturn(missingEnrichmentUpdatedAt);
        ResultSet missingStructuredDataUpper = resultSet(false);
        ResultSet missingDemandStatusUpper = resultSet(false);
        ResultSet missingEnrichmentStatusUpper = resultSet(false);
        ResultSet missingEnrichmentErrorSummaryUpper = resultSet(false);
        ResultSet missingEnrichmentUpdatedAtUpper = resultSet(false);
        when(metaData.getColumns("workhub", null, "PM_INTAKE_RECORD", "STRUCTURED_DATA_JSON")).thenReturn(missingStructuredDataUpper);
        when(metaData.getColumns("workhub", null, "PM_INTAKE_RECORD", "DEMAND_STATUS")).thenReturn(missingDemandStatusUpper);
        when(metaData.getColumns("workhub", null, "PM_INTAKE_RECORD", "ENRICHMENT_STATUS")).thenReturn(missingEnrichmentStatusUpper);
        when(metaData.getColumns("workhub", null, "PM_INTAKE_RECORD", "ENRICHMENT_ERROR_SUMMARY")).thenReturn(missingEnrichmentErrorSummaryUpper);
        when(metaData.getColumns("workhub", null, "PM_INTAKE_RECORD", "ENRICHMENT_UPDATED_AT")).thenReturn(missingEnrichmentUpdatedAtUpper);

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

        verify(statement).execute("ALTER TABLE `pm_intake_record` ADD COLUMN `structured_data_json` TEXT NULL");
        verify(statement).execute("ALTER TABLE `pm_intake_record` ADD COLUMN `demand_status` VARCHAR(32) NULL");
        verify(statement).execute("ALTER TABLE `pm_intake_record` ADD COLUMN `enrichment_status` VARCHAR(16) NULL");
        verify(statement).execute("ALTER TABLE `pm_intake_record` ADD COLUMN `enrichment_error_summary` VARCHAR(255) NULL");
        verify(statement).execute("ALTER TABLE `pm_intake_record` ADD COLUMN `enrichment_updated_at` DATETIME NULL");
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

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.getCatalog()).thenReturn("workhub");
        when(connection.createStatement()).thenReturn(statement);

        ResultSet tableExists = resultSet(true);
        when(metaData.getTables("workhub", null, "pm_intake_record", new String[]{"TABLE"})).thenReturn(tableExists);

        ResultSet existingStructuredData = resultSet(true);
        ResultSet existingDemandStatus = resultSet(true);
        ResultSet existingEnrichmentStatus = resultSet(true);
        ResultSet existingEnrichmentErrorSummary = resultSet(true);
        ResultSet existingEnrichmentUpdatedAt = resultSet(true);
        when(metaData.getColumns("workhub", null, "pm_intake_record", "structured_data_json")).thenReturn(existingStructuredData);
        when(metaData.getColumns("workhub", null, "pm_intake_record", "demand_status")).thenReturn(existingDemandStatus);
        when(metaData.getColumns("workhub", null, "pm_intake_record", "enrichment_status")).thenReturn(existingEnrichmentStatus);
        when(metaData.getColumns("workhub", null, "pm_intake_record", "enrichment_error_summary")).thenReturn(existingEnrichmentErrorSummary);
        when(metaData.getColumns("workhub", null, "pm_intake_record", "enrichment_updated_at")).thenReturn(existingEnrichmentUpdatedAt);

        ResultSet existingIndex = resultSetWithIndex("uk_pm_intake_record_external_message_id");
        when(metaData.getIndexInfo("workhub", null, "pm_intake_record", true, false)).thenReturn(existingIndex);
        ResultSet historyExists = resultSet(true);
        when(metaData.getTables("workhub", null, "pm_intake_history", new String[]{"TABLE"})).thenReturn(historyExists);

        IntakeSchemaPatchRunner runner = new IntakeSchemaPatchRunner(dataSource);
        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(statement, never()).execute(org.mockito.ArgumentMatchers.anyString());
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
}
