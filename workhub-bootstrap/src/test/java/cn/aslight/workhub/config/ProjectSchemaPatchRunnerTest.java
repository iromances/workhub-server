package cn.aslight.workhub.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectSchemaPatchRunnerTest {

    @Test
    void run_shouldCreateBusinessLineTableWhenMissing() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.getCatalog()).thenReturn("workhub");
        when(connection.createStatement()).thenReturn(statement);

        when(metaData.getTables(anyString(), isNull(), anyString(), any())).thenAnswer(invocation -> resultSet(false));
        when(metaData.getColumns(anyString(), isNull(), anyString(), anyString())).thenAnswer(invocation -> resultSet(false));
        when(metaData.getTables(eq("workhub"), isNull(), eq("pm_project"), any())).thenAnswer(invocation -> resultSet(true));

        ProjectSchemaPatchRunner runner = new ProjectSchemaPatchRunner(dataSource);
        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(statement).execute(contains("ADD COLUMN `business_line`"));
        verify(statement).execute(contains("CREATE TABLE `pm_business_line`"));
    }

    @Test
    void run_shouldMigrateLegacyProjectGroupSystemScope() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.getCatalog()).thenReturn("workhub");
        when(connection.createStatement()).thenReturn(statement);

        when(metaData.getTables(anyString(), isNull(), anyString(), any())).thenAnswer(invocation -> {
            String tableName = invocation.getArgument(2);
            return resultSet("pm_project_involved_system".equals(tableName));
        });
        when(metaData.getColumns(anyString(), isNull(), anyString(), anyString())).thenAnswer(invocation -> {
            String tableName = invocation.getArgument(2);
            String columnName = invocation.getArgument(3);
            return resultSet("pm_project_involved_system".equals(tableName) && "system_scope".equals(columnName));
        });

        ProjectSchemaPatchRunner runner = new ProjectSchemaPatchRunner(dataSource);
        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(statement).execute(contains("SET `system_scope` = 'BUSINESS_LINE' WHERE `system_scope` = 'PROJECT_GROUP'"));
    }

    private ResultSet resultSet(boolean firstNext) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(firstNext, false);
        return resultSet;
    }
}
