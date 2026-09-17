package cn.aslight.workhub.maintenance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttachmentContentBackfillCliTest {

    @Test
    void dryRunShouldReportCandidateWithoutUpdatingDatabase(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("legacy.txt");
        Files.writeString(file, "legacy-content");
        JdbcMocks jdbc = jdbc(file, 0);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        AttachmentContentBackfillCli.BackfillResult result = AttachmentContentBackfillCli.run(
                jdbc.connection(),
                false,
                new PrintStream(output)
        );

        assertEquals(1, result.candidateCount());
        assertEquals(0, result.updatedCount());
        verify(jdbc.updateStatement(), never()).executeUpdate();
        assertTrue(output.toString().contains("mode=DRY_RUN"));
    }

    @Test
    void applyShouldUpdateExistingFileAndCommit(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("legacy.txt");
        Files.writeString(file, "legacy-content");
        JdbcMocks jdbc = jdbc(file, 1);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        AttachmentContentBackfillCli.BackfillResult result = AttachmentContentBackfillCli.run(
                jdbc.connection(),
                true,
                new PrintStream(output)
        );

        assertEquals(1, result.updatedCount());
        assertEquals(0, result.failedCount());
        verify(jdbc.connection()).commit();
        verify(jdbc.updateStatement()).setLong(4, 328L);
        assertTrue(output.toString().contains("UPDATED id=328"));
    }

    private JdbcMocks jdbc(Path file, int affectedRows) throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement selectStatement = mock(PreparedStatement.class);
        PreparedStatement updateStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(selectStatement, updateStatement);
        when(selectStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getLong("id")).thenReturn(328L);
        when(resultSet.getString("storage_path")).thenReturn(file.toString());
        when(resultSet.getBoolean("content_stored")).thenReturn(false);
        when(updateStatement.executeUpdate()).thenReturn(affectedRows);
        return new JdbcMocks(connection, updateStatement);
    }

    private record JdbcMocks(Connection connection, PreparedStatement updateStatement) {
    }
}
