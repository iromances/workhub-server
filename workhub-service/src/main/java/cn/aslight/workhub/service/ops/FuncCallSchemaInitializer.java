package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.FuncCallSqlMapper;
import cn.aslight.workhub.model.ops.FuncCallSqlEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/**
 * REQ-9f3c2a84：func_call_sql 表结构与种子初始化。DDL 与 Flyway V20261114_110000 一致
 * （本地/历史环境未跑迁移时兜底建表）；种子按 sql_code 幂等回写，已存在（含页面维护过的）
 * 不覆盖，保证库内定义优先。
 */
@Service
public class FuncCallSchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(FuncCallSchemaInitializer.class);
    private static final String TABLE_NAME = "func_call_sql";
    private static final String AUDIT_TABLE_NAME = "func_call_sql_audit_log";

    private final DataSource dataSource;
    private final FuncCallSqlMapper funcCallSqlMapper;
    private volatile boolean initialized;

    public FuncCallSchemaInitializer(DataSource dataSource, FuncCallSqlMapper funcCallSqlMapper) {
        this.dataSource = dataSource;
        this.funcCallSqlMapper = funcCallSqlMapper;
    }

    public synchronized void ensureInitialized() {
        if (initialized) {
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            if (!tableExists(connection)) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("""
                            CREATE TABLE `func_call_sql` (
                              `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                              `sql_code` VARCHAR(64) NOT NULL COMMENT 'SQL 编码（唯一，调用方可直接传此编码）',
                              `scenario_code` VARCHAR(64) NULL COMMENT '业务场景编码（一个场景可映射多条分支模板，按 branch_order 组合）',
                              `branch_order` INT NOT NULL DEFAULT 1 COMMENT '同场景多分支模板的组合顺序',
                              `name` VARCHAR(128) NOT NULL COMMENT '模板名称',
                              `description` VARCHAR(512) NULL COMMENT '用途说明',
                              `sql_template` MEDIUMTEXT NOT NULL COMMENT 'SQL 模板（:name 命名参数，值由调用方白名单校验后传入）',
                              `param_desc` VARCHAR(1024) NULL COMMENT '参数说明（名称、含义、是否可空）',
                              `source_module` VARCHAR(64) NULL COMMENT '来源模块（如 OPS_XXL / OPS_MQ）',
                              `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用（禁用后调用报错）',
                              `remark` VARCHAR(255) NULL COMMENT '备注',
                              `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                              `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                              PRIMARY KEY (`id`),
                              UNIQUE KEY `uk_func_call_sql_code` (`sql_code`),
                              KEY `idx_func_call_scenario` (`scenario_code`, `branch_order`)
                            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Function Call 场景 SQL 登记表（业务库只读查询统一入口）'
                            """);
                    log.warn("Created missing table {}", TABLE_NAME);
                }
            }
            if (!auditTableExists(connection)) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("""
                            CREATE TABLE `func_call_sql_audit_log` (
                              `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                              `sql_code` VARCHAR(160) NOT NULL COMMENT 'SQL 编码（组合语句为参与码 + 连接）',
                              `scenario_code` VARCHAR(64) NULL COMMENT '场景编码（单编码可推导时填写）',
                              `source` VARCHAR(16) NOT NULL COMMENT '执行来源：SYSTEM（巡检/代码）/ MANUAL（页面试运行）',
                              `operator` VARCHAR(64) NOT NULL COMMENT '操作人（页面为登录用户，巡检为 SYSTEM）',
                              `business_line_code` VARCHAR(128) NOT NULL COMMENT '业务线编码',
                              `environment_code` VARCHAR(64) NOT NULL COMMENT '环境编码',
                              `rendered_sql` MEDIUMTEXT NOT NULL COMMENT '渲染后实际执行 SQL 全文（审计证据）',
                              `status` VARCHAR(16) NOT NULL COMMENT '执行状态：SUCCESS / ERROR',
                              `row_count` INT NULL COMMENT '返回行数',
                              `duration_ms` BIGINT NULL COMMENT '执行耗时毫秒（批次语句共享整批计时）',
                              `error_message` TEXT NULL COMMENT '失败原因',
                              `started_at` DATETIME(3) NOT NULL COMMENT '开始时间',
                              `finished_at` DATETIME(3) NULL COMMENT '结束时间',
                              `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                              PRIMARY KEY (`id`),
                              KEY `idx_func_audit_code_time` (`sql_code`, `started_at`),
                              KEY `idx_func_audit_time` (`started_at`)
                            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Function Call 执行审计（保留 365 天）'
                            """);
                    log.warn("Created missing table {}", AUDIT_TABLE_NAME);
                }
            }
            int seeded = 0;
            for (FuncCallSqlSeeds.Seed seed : java.util.stream.Stream.concat(
                    FuncCallSqlSeeds.SEEDS.stream(), AccountingIntegrityCatalog.seeds().stream()).toList()) {
                if (funcCallSqlMapper.findByCode(seed.sqlCode()) == null) {
                    funcCallSqlMapper.insert(seed.toEntity());
                    seeded++;
                }
            }
            if (seeded > 0) {
                log.info("Seeded {} func_call_sql templates", seeded);
            }
            initialized = true;
        } catch (SQLException ex) {
            throw new IllegalStateException("初始化 Function Call SQL 登记表失败：" + ex.getMessage(), ex);
        }
    }

    private boolean tableExists(Connection connection) throws SQLException {
        return tableExists(connection, TABLE_NAME);
    }

    private boolean auditTableExists(Connection connection) throws SQLException {
        return tableExists(connection, AUDIT_TABLE_NAME);
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        return tableExists(metaData, connection.getCatalog(), tableName)
                || tableExists(metaData, connection.getCatalog(), tableName.toUpperCase(Locale.ROOT))
                || tableExists(metaData, connection.getCatalog(), tableName.toLowerCase(Locale.ROOT));
    }

    private boolean tableExists(DatabaseMetaData metaData, String catalog, String tableName) throws SQLException {
        try (ResultSet resultSet = metaData.getTables(catalog, null, tableName, new String[]{"TABLE"})) {
            return resultSet.next();
        }
    }
}
