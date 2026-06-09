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
import java.util.List;
import java.util.Locale;

/**
 * 支付配置域建表补丁。
 */
@Component
public class PaymentSchemaPatchRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PaymentSchemaPatchRunner.class);

    private static final List<TablePatch> TABLE_PATCHES = List.of(
            new TablePatch(
                    "pay_channel",
                    """
                            CREATE TABLE `pay_channel` (
                              `id` BIGINT NOT NULL AUTO_INCREMENT,
                              `channel_code` VARCHAR(64) NOT NULL,
                              `channel_name` VARCHAR(128) NOT NULL,
                              `vendor_name` VARCHAR(128) NOT NULL,
                              `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                              `description` TEXT NULL,
                              `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                              PRIMARY KEY (`id`),
                              UNIQUE KEY `uk_pay_channel_code` (`channel_code`)
                            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                            """
            ),
            new TablePatch(
                    "pay_merchant_account",
                    """
                            CREATE TABLE `pay_merchant_account` (
                              `id` BIGINT NOT NULL AUTO_INCREMENT,
                              `channel_id` BIGINT NOT NULL,
                              `merchant_code` VARCHAR(128) NOT NULL,
                              `merchant_name` VARCHAR(128) NOT NULL,
                              `environment` VARCHAR(32) NOT NULL,
                              `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                              `app_id` VARCHAR(128) NULL,
                              `settlement_subject` VARCHAR(128) NULL,
                              `remark` TEXT NULL,
                              `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                              PRIMARY KEY (`id`),
                              UNIQUE KEY `uk_pay_merchant_channel_code_env` (`channel_id`, `merchant_code`, `environment`),
                              KEY `idx_pay_merchant_channel_id` (`channel_id`)
                            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                            """
            ),
            new TablePatch(
                    "pay_merchant_purpose",
                    """
                            CREATE TABLE `pay_merchant_purpose` (
                              `id` BIGINT NOT NULL AUTO_INCREMENT,
                              `merchant_id` BIGINT NOT NULL,
                              `purpose_code` VARCHAR(32) NOT NULL,
                              `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              PRIMARY KEY (`id`),
                              UNIQUE KEY `uk_pay_merchant_purpose` (`merchant_id`, `purpose_code`),
                              KEY `idx_pay_merchant_purpose_code` (`purpose_code`, `merchant_id`)
                            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                            """
            ),
            new TablePatch(
                    "pay_merchant_param",
                    """
                            CREATE TABLE `pay_merchant_param` (
                              `id` BIGINT NOT NULL AUTO_INCREMENT,
                              `merchant_id` BIGINT NOT NULL,
                              `param_key` VARCHAR(128) NOT NULL,
                              `value_type` VARCHAR(32) NOT NULL,
                              `sensitive_flag` TINYINT(1) NOT NULL DEFAULT 0,
                              `plain_value` TEXT NULL,
                              `encrypted_value` TEXT NULL,
                              `masked_value` VARCHAR(255) NULL,
                              `remark` VARCHAR(255) NULL,
                              `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                              PRIMARY KEY (`id`),
                              UNIQUE KEY `uk_pay_merchant_param_merchant_key` (`merchant_id`, `param_key`),
                              KEY `idx_pay_merchant_param_merchant_id` (`merchant_id`)
                            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                            """
            ),
            new TablePatch(
                    "pay_merchant_secret",
                    """
                            CREATE TABLE `pay_merchant_secret` (
                              `id` BIGINT NOT NULL AUTO_INCREMENT,
                              `merchant_id` BIGINT NOT NULL,
                              `secret_name` VARCHAR(128) NOT NULL,
                              `secret_type` VARCHAR(32) NOT NULL,
                              `encrypted_value` TEXT NOT NULL,
                              `masked_value` VARCHAR(255) NOT NULL,
                              `fingerprint` VARCHAR(128) NOT NULL,
                              `algorithm` VARCHAR(64) NOT NULL,
                              `version_no` INT NOT NULL,
                              `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                              `valid_from` DATETIME NULL,
                              `valid_to` DATETIME NULL,
                              `remark` VARCHAR(255) NULL,
                              `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                              PRIMARY KEY (`id`),
                              UNIQUE KEY `uk_pay_merchant_secret_name_version` (`merchant_id`, `secret_name`, `version_no`),
                              KEY `idx_pay_merchant_secret_lookup` (`merchant_id`, `secret_name`, `status`)
                            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                            """
            ),
            new TablePatch(
                    "pay_project_merchant_binding",
                    """
                            CREATE TABLE `pay_project_merchant_binding` (
                              `id` BIGINT NOT NULL AUTO_INCREMENT,
                              `project_id` BIGINT NOT NULL,
                              `merchant_id` BIGINT NOT NULL,
                              `purpose_code` VARCHAR(32) NOT NULL,
                              `priority` INT NOT NULL DEFAULT 1,
                              `is_default` TINYINT(1) NOT NULL DEFAULT 0,
                              `binding_status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                              `remark` VARCHAR(255) NULL,
                              `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                              PRIMARY KEY (`id`),
                              UNIQUE KEY `uk_pay_binding_project_merchant_purpose` (`project_id`, `merchant_id`, `purpose_code`),
                              KEY `idx_pay_binding_project_purpose` (`project_id`, `purpose_code`, `binding_status`, `is_default`, `priority`),
                              KEY `idx_pay_binding_merchant_id` (`merchant_id`)
                            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                            """
            ),
            new TablePatch(
                    "pay_project_merchant_binding_purpose",
                    """
                            CREATE TABLE `pay_project_merchant_binding_purpose` (
                              `id` BIGINT NOT NULL AUTO_INCREMENT,
                              `binding_id` BIGINT NOT NULL,
                              `purpose_code` VARCHAR(32) NOT NULL,
                              `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              PRIMARY KEY (`id`),
                              UNIQUE KEY `uk_pay_binding_purpose` (`binding_id`, `purpose_code`),
                              KEY `idx_pay_binding_purpose_code` (`purpose_code`, `binding_id`)
                            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                            """
            ),
            new TablePatch(
                    "pay_project_merchant_binding_relation",
                    """
                            CREATE TABLE `pay_project_merchant_binding_relation` (
                              `id` BIGINT NOT NULL AUTO_INCREMENT,
                              `binding_id` BIGINT NOT NULL,
                              `merchant_id` BIGINT NOT NULL,
                              `relation_role` VARCHAR(32) NOT NULL,
                              `relation_name` VARCHAR(128) NULL,
                              `priority` INT NOT NULL DEFAULT 1,
                              `remark` VARCHAR(255) NULL,
                              `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                              PRIMARY KEY (`id`),
                              KEY `idx_pay_binding_relation_binding` (`binding_id`, `relation_role`, `priority`),
                              KEY `idx_pay_binding_relation_merchant` (`merchant_id`)
                            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                            """
            ),
            new TablePatch(
                    "pay_merchant_credential",
                    """
                            CREATE TABLE `pay_merchant_credential` (
                              `id` BIGINT NOT NULL AUTO_INCREMENT,
                              `merchant_id` BIGINT NOT NULL,
                              `credential_key` VARCHAR(128) NOT NULL,
                              `credential_name` VARCHAR(128) NOT NULL,
                              `credential_type` VARCHAR(32) NOT NULL,
                              `encrypted_value` TEXT NOT NULL,
                              `masked_value` VARCHAR(255) NOT NULL,
                              `fingerprint` VARCHAR(128) NOT NULL,
                              `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                              `remark` VARCHAR(255) NULL,
                              `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                              PRIMARY KEY (`id`),
                              UNIQUE KEY `uk_pay_merchant_credential_key` (`merchant_id`, `credential_key`),
                              KEY `idx_pay_merchant_credential_merchant` (`merchant_id`, `status`)
                            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                            """
            ),
            new TablePatch(
                    "pay_operation_log",
                    """
                            CREATE TABLE `pay_operation_log` (
                              `id` BIGINT NOT NULL AUTO_INCREMENT,
                              `biz_type` VARCHAR(32) NOT NULL,
                              `biz_id` BIGINT NOT NULL,
                              `action_type` VARCHAR(32) NOT NULL,
                              `action_summary` VARCHAR(128) NOT NULL,
                              `detail_text` TEXT NULL,
                              `operator_user_name` VARCHAR(64) NOT NULL,
                              `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              PRIMARY KEY (`id`),
                              KEY `idx_pay_operation_log_biz` (`biz_type`, `biz_id`, `created_at`)
                            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                            """
            )
    );

    private final DataSource dataSource;

    public PaymentSchemaPatchRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            for (TablePatch patch : TABLE_PATCHES) {
                ensureTable(connection, patch);
            }
            ensureBindingPurposeBackfill(connection);
            ensureMerchantPurposeBackfill(connection);
            ensureLegacyWithholdPurposeMerge(connection);
        }
    }

    private void ensureBindingPurposeBackfill(Connection connection) throws SQLException {
        if (!tableExists(connection, "pay_project_merchant_binding")
                || !tableExists(connection, "pay_project_merchant_binding_purpose")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT IGNORE INTO `pay_project_merchant_binding_purpose` (`binding_id`, `purpose_code`)
                    SELECT `id`, `purpose_code`
                    FROM `pay_project_merchant_binding`
                    WHERE `purpose_code` IS NOT NULL
                      AND `purpose_code` <> ''
                    """);
        }
    }

    private void ensureMerchantPurposeBackfill(Connection connection) throws SQLException {
        if (!tableExists(connection, "pay_project_merchant_binding")
                || !tableExists(connection, "pay_project_merchant_binding_purpose")
                || !tableExists(connection, "pay_merchant_purpose")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT IGNORE INTO `pay_merchant_purpose` (`merchant_id`, `purpose_code`)
                    SELECT DISTINCT b.`merchant_id`, bp.`purpose_code`
                    FROM `pay_project_merchant_binding` b
                    JOIN `pay_project_merchant_binding_purpose` bp ON bp.`binding_id` = b.`id`
                    WHERE bp.`purpose_code` IS NOT NULL
                      AND bp.`purpose_code` <> ''
                    """);
        }
    }

    private void ensureLegacyWithholdPurposeMerge(Connection connection) throws SQLException {
        if (!tableExists(connection, "pay_project_merchant_binding")
                || !tableExists(connection, "pay_project_merchant_binding_purpose")
                || !tableExists(connection, "pay_merchant_purpose")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT IGNORE INTO `pay_merchant_purpose` (`merchant_id`, `purpose_code`)
                    SELECT DISTINCT `merchant_id`, 'WITHHOLD'
                    FROM `pay_merchant_purpose`
                    WHERE `purpose_code` IN (
                      'WITHHOLD_SUB_MERCHANT',
                      'WITHHOLD_SUB_MERCHANT_BEIJING',
                      'WITHHOLD_SUB_MERCHANT_TIANJIN',
                      'WITHHOLD_SUB_MERCHANT_PROD_TEST'
                    )
                    """);
            statement.execute("""
                    DELETE FROM `pay_merchant_purpose`
                    WHERE `purpose_code` IN (
                      'WITHHOLD_SUB_MERCHANT',
                      'WITHHOLD_SUB_MERCHANT_BEIJING',
                      'WITHHOLD_SUB_MERCHANT_TIANJIN',
                      'WITHHOLD_SUB_MERCHANT_PROD_TEST'
                    )
                    """);
            statement.execute("""
                    INSERT IGNORE INTO `pay_project_merchant_binding_purpose` (`binding_id`, `purpose_code`)
                    SELECT DISTINCT `binding_id`, 'WITHHOLD'
                    FROM `pay_project_merchant_binding_purpose`
                    WHERE `purpose_code` IN (
                      'WITHHOLD_SUB_MERCHANT',
                      'WITHHOLD_SUB_MERCHANT_BEIJING',
                      'WITHHOLD_SUB_MERCHANT_TIANJIN',
                      'WITHHOLD_SUB_MERCHANT_PROD_TEST'
                    )
                    """);
            statement.execute("""
                    DELETE FROM `pay_project_merchant_binding_purpose`
                    WHERE `purpose_code` IN (
                      'WITHHOLD_SUB_MERCHANT',
                      'WITHHOLD_SUB_MERCHANT_BEIJING',
                      'WITHHOLD_SUB_MERCHANT_TIANJIN',
                      'WITHHOLD_SUB_MERCHANT_PROD_TEST'
                    )
                    """);
            statement.execute("""
                    UPDATE `pay_project_merchant_binding` b
                    LEFT JOIN `pay_project_merchant_binding` existing
                      ON existing.`project_id` = b.`project_id`
                     AND existing.`merchant_id` = b.`merchant_id`
                     AND existing.`purpose_code` = 'WITHHOLD'
                     AND existing.`id` <> b.`id`
                    LEFT JOIN `pay_project_merchant_binding` earlier
                      ON earlier.`project_id` = b.`project_id`
                     AND earlier.`merchant_id` = b.`merchant_id`
                     AND earlier.`purpose_code` IN (
                       'WITHHOLD_SUB_MERCHANT',
                       'WITHHOLD_SUB_MERCHANT_BEIJING',
                       'WITHHOLD_SUB_MERCHANT_TIANJIN',
                       'WITHHOLD_SUB_MERCHANT_PROD_TEST'
                     )
                     AND earlier.`id` < b.`id`
                    SET b.`purpose_code` = 'WITHHOLD'
                    WHERE b.`purpose_code` IN (
                      'WITHHOLD_SUB_MERCHANT',
                      'WITHHOLD_SUB_MERCHANT_BEIJING',
                      'WITHHOLD_SUB_MERCHANT_TIANJIN',
                      'WITHHOLD_SUB_MERCHANT_PROD_TEST'
                    )
                    AND existing.`id` IS NULL
                    AND earlier.`id` IS NULL
                    """);
        }
    }

    void ensureTable(Connection connection, TablePatch patch) throws SQLException {
        if (tableExists(connection, patch.tableName())) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(patch.createSql());
            log.warn("Created missing payment table {}", patch.tableName());
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to create payment table with SQL: " + patch.createSql(), ex);
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

    record TablePatch(String tableName, String createSql) {
    }
}
