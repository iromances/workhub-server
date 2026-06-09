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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentSchemaPatchRunnerTest {

    @Test
    void run_shouldCreateMissingPaymentTables() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.getCatalog()).thenReturn("workhub");
        when(connection.createStatement()).thenReturn(statement);

        ResultSet payChannelMissing = resultSet(false);
        ResultSet payChannelMissingUpper = resultSet(false);
        ResultSet payMerchantAccountMissing = resultSet(false);
        ResultSet payMerchantAccountMissingUpper = resultSet(false);
        ResultSet payMerchantPurposeMissing = resultSet(false);
        ResultSet payMerchantPurposeMissingUpper = resultSet(false);
        ResultSet payMerchantParamMissing = resultSet(false);
        ResultSet payMerchantParamMissingUpper = resultSet(false);
        ResultSet payMerchantSecretMissing = resultSet(false);
        ResultSet payMerchantSecretMissingUpper = resultSet(false);
        ResultSet payBindingMissing = resultSet(false);
        ResultSet payBindingMissingUpper = resultSet(false);
        ResultSet payBindingPurposeMissing = resultSet(false);
        ResultSet payBindingPurposeMissingUpper = resultSet(false);
        ResultSet payBindingRelationMissing = resultSet(false);
        ResultSet payBindingRelationMissingUpper = resultSet(false);
        ResultSet payMerchantCredentialMissing = resultSet(false);
        ResultSet payMerchantCredentialMissingUpper = resultSet(false);
        ResultSet payOperationLogMissing = resultSet(false);
        ResultSet payOperationLogMissingUpper = resultSet(false);

        when(metaData.getTables("workhub", null, "pay_channel", new String[]{"TABLE"})).thenReturn(payChannelMissing);
        when(metaData.getTables("workhub", null, "PAY_CHANNEL", new String[]{"TABLE"})).thenReturn(payChannelMissingUpper);
        when(metaData.getTables("workhub", null, "pay_merchant_account", new String[]{"TABLE"})).thenReturn(payMerchantAccountMissing);
        when(metaData.getTables("workhub", null, "PAY_MERCHANT_ACCOUNT", new String[]{"TABLE"})).thenReturn(payMerchantAccountMissingUpper);
        when(metaData.getTables("workhub", null, "pay_merchant_purpose", new String[]{"TABLE"})).thenReturn(payMerchantPurposeMissing);
        when(metaData.getTables("workhub", null, "PAY_MERCHANT_PURPOSE", new String[]{"TABLE"})).thenReturn(payMerchantPurposeMissingUpper);
        when(metaData.getTables("workhub", null, "pay_merchant_param", new String[]{"TABLE"})).thenReturn(payMerchantParamMissing);
        when(metaData.getTables("workhub", null, "PAY_MERCHANT_PARAM", new String[]{"TABLE"})).thenReturn(payMerchantParamMissingUpper);
        when(metaData.getTables("workhub", null, "pay_merchant_secret", new String[]{"TABLE"})).thenReturn(payMerchantSecretMissing);
        when(metaData.getTables("workhub", null, "PAY_MERCHANT_SECRET", new String[]{"TABLE"})).thenReturn(payMerchantSecretMissingUpper);
        when(metaData.getTables("workhub", null, "pay_project_merchant_binding", new String[]{"TABLE"})).thenReturn(payBindingMissing);
        when(metaData.getTables("workhub", null, "PAY_PROJECT_MERCHANT_BINDING", new String[]{"TABLE"})).thenReturn(payBindingMissingUpper);
        when(metaData.getTables("workhub", null, "pay_project_merchant_binding_purpose", new String[]{"TABLE"})).thenReturn(payBindingPurposeMissing);
        when(metaData.getTables("workhub", null, "PAY_PROJECT_MERCHANT_BINDING_PURPOSE", new String[]{"TABLE"})).thenReturn(payBindingPurposeMissingUpper);
        when(metaData.getTables("workhub", null, "pay_project_merchant_binding_relation", new String[]{"TABLE"})).thenReturn(payBindingRelationMissing);
        when(metaData.getTables("workhub", null, "PAY_PROJECT_MERCHANT_BINDING_RELATION", new String[]{"TABLE"})).thenReturn(payBindingRelationMissingUpper);
        when(metaData.getTables("workhub", null, "pay_merchant_credential", new String[]{"TABLE"})).thenReturn(payMerchantCredentialMissing);
        when(metaData.getTables("workhub", null, "PAY_MERCHANT_CREDENTIAL", new String[]{"TABLE"})).thenReturn(payMerchantCredentialMissingUpper);
        when(metaData.getTables("workhub", null, "pay_operation_log", new String[]{"TABLE"})).thenReturn(payOperationLogMissing);
        when(metaData.getTables("workhub", null, "PAY_OPERATION_LOG", new String[]{"TABLE"})).thenReturn(payOperationLogMissingUpper);

        PaymentSchemaPatchRunner runner = new PaymentSchemaPatchRunner(dataSource);
        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(statement).execute(org.mockito.ArgumentMatchers.contains("CREATE TABLE `pay_channel`"));
        verify(statement).execute(org.mockito.ArgumentMatchers.contains("CREATE TABLE `pay_merchant_account`"));
        verify(statement).execute(org.mockito.ArgumentMatchers.contains("CREATE TABLE `pay_merchant_purpose`"));
        verify(statement).execute(org.mockito.ArgumentMatchers.contains("CREATE TABLE `pay_merchant_param`"));
        verify(statement).execute(org.mockito.ArgumentMatchers.contains("CREATE TABLE `pay_merchant_secret`"));
        verify(statement).execute(org.mockito.ArgumentMatchers.contains("CREATE TABLE `pay_project_merchant_binding`"));
        verify(statement).execute(org.mockito.ArgumentMatchers.contains("CREATE TABLE `pay_project_merchant_binding_purpose`"));
        verify(statement).execute(org.mockito.ArgumentMatchers.contains("CREATE TABLE `pay_project_merchant_binding_relation`"));
        verify(statement).execute(org.mockito.ArgumentMatchers.contains("CREATE TABLE `pay_merchant_credential`"));
        verify(statement).execute(org.mockito.ArgumentMatchers.contains("CREATE TABLE `pay_operation_log`"));
    }

    @Test
    void run_shouldSkipWhenPaymentTablesAlreadyExist() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.getCatalog()).thenReturn("workhub");
        when(connection.createStatement()).thenReturn(statement);

        ResultSet payChannelExists = resultSet(true);
        ResultSet payMerchantAccountExists = resultSet(true);
        ResultSet payMerchantPurposeExists = resultSet(true);
        ResultSet payMerchantParamExists = resultSet(true);
        ResultSet payMerchantSecretExists = resultSet(true);
        ResultSet payBindingExists = resultSet(true);
        ResultSet payBindingPurposeExists = resultSet(true);
        ResultSet payBindingRelationExists = resultSet(true);
        ResultSet payMerchantCredentialExists = resultSet(true);
        ResultSet payOperationLogExists = resultSet(true);
        ResultSet payBindingExistsForBackfill = resultSet(true);
        ResultSet payBindingPurposeExistsForBackfill = resultSet(true);
        ResultSet payBindingExistsForMerchantPurposeBackfill = resultSet(true);
        ResultSet payBindingPurposeExistsForMerchantPurposeBackfill = resultSet(true);
        ResultSet payMerchantPurposeExistsForBackfill = resultSet(true);
        ResultSet payBindingExistsForLegacyMerge = resultSet(true);
        ResultSet payBindingPurposeExistsForLegacyMerge = resultSet(true);
        ResultSet payMerchantPurposeExistsForLegacyMerge = resultSet(true);

        when(metaData.getTables("workhub", null, "pay_channel", new String[]{"TABLE"})).thenReturn(payChannelExists);
        when(metaData.getTables("workhub", null, "pay_merchant_account", new String[]{"TABLE"})).thenReturn(payMerchantAccountExists);
        when(metaData.getTables("workhub", null, "pay_merchant_purpose", new String[]{"TABLE"})).thenReturn(payMerchantPurposeExists, payMerchantPurposeExistsForBackfill, payMerchantPurposeExistsForLegacyMerge);
        when(metaData.getTables("workhub", null, "pay_merchant_param", new String[]{"TABLE"})).thenReturn(payMerchantParamExists);
        when(metaData.getTables("workhub", null, "pay_merchant_secret", new String[]{"TABLE"})).thenReturn(payMerchantSecretExists);
        when(metaData.getTables("workhub", null, "pay_project_merchant_binding", new String[]{"TABLE"})).thenReturn(payBindingExists, payBindingExistsForBackfill, payBindingExistsForMerchantPurposeBackfill, payBindingExistsForLegacyMerge);
        when(metaData.getTables("workhub", null, "pay_project_merchant_binding_purpose", new String[]{"TABLE"})).thenReturn(payBindingPurposeExists, payBindingPurposeExistsForBackfill, payBindingPurposeExistsForMerchantPurposeBackfill, payBindingPurposeExistsForLegacyMerge);
        when(metaData.getTables("workhub", null, "pay_project_merchant_binding_relation", new String[]{"TABLE"})).thenReturn(payBindingRelationExists);
        when(metaData.getTables("workhub", null, "pay_merchant_credential", new String[]{"TABLE"})).thenReturn(payMerchantCredentialExists);
        when(metaData.getTables("workhub", null, "pay_operation_log", new String[]{"TABLE"})).thenReturn(payOperationLogExists);

        PaymentSchemaPatchRunner runner = new PaymentSchemaPatchRunner(dataSource);
        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(statement, never()).execute(org.mockito.ArgumentMatchers.contains("CREATE TABLE"));
        verify(statement, atLeastOnce()).execute(org.mockito.ArgumentMatchers.contains("INSERT IGNORE INTO `pay_project_merchant_binding_purpose`"));
        verify(statement, atLeastOnce()).execute(org.mockito.ArgumentMatchers.contains("INSERT IGNORE INTO `pay_merchant_purpose`"));
        verify(statement, atLeastOnce()).execute(org.mockito.ArgumentMatchers.contains("WITHHOLD_SUB_MERCHANT_PROD_TEST"));
        verify(statement).execute(org.mockito.ArgumentMatchers.contains("UPDATE `pay_project_merchant_binding` b"));
    }

    private ResultSet resultSet(boolean firstNext) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(firstNext, false);
        return resultSet;
    }
}
