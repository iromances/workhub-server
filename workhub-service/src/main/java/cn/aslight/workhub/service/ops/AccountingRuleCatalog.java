package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.model.ops.AccountingRuleResponse;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class AccountingRuleCatalog {

    public static final String HOURLY = "HOURLY";
    public static final String DAILY = "DAILY";
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_]+");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int SAMPLE_LIMIT = 20;

    private final List<AccountingRuleDefinition> commonRules;
    private final Map<String, List<AccountingRuleDefinition>> profileRules;

    public AccountingRuleCatalog() {
        commonRules = List.of(
                transactionDuplicateRule(),
                transactionAmountRule(),
                pendTransactionAmountRule(),
                statementResourceRule(),
                paySubjectAmountRule(),
                repaymentPlanAmountRule(),
                settledPlanRule(),
                statementAmountRule()
        );
        profileRules = new LinkedHashMap<>();
        for (String profile : List.of(
                "COMMON_SAPS", "SCF_SAPS", "LEASE_SAPS", "JIATAI_AMP_SAPS",
                "EQUIP_LEASE_SAPS", "OL_SAPS")) {
            profileRules.put(profile, commonRules);
        }
        profileRules.put("ASSETS_SAPS", append(commonRules, assetsStatementCompareRule(), assetsChannelReconciliationRule()));
        profileRules.put("AMP_SAPS", append(commonRules, ampRefactoringRequiredFieldsRule()));
        profileRules.put("LOGISTICS_SAPS", append(commonRules,
                logisticsOrderTradeRule(),
                logisticsBillPayDetailRule(),
                logisticsPaySubjectDetailRule()));
    }

    public List<AccountingRuleDefinition> rulesForProfile(String ruleProfile) {
        List<AccountingRuleDefinition> rules = profileRules.get(ruleProfile);
        if (rules == null) {
            throw new IllegalArgumentException("不支持的账务规则包：" + ruleProfile);
        }
        return rules;
    }

    public List<AccountingRuleResponse> responses(String ruleProfile) {
        return rulesForProfile(ruleProfile).stream()
                .map(rule -> new AccountingRuleResponse(
                        rule.ruleCode(),
                        rule.ruleName(),
                        rule.category(),
                        rule.granularity(),
                        rule.severity(),
                        rule.description(),
                        ruleProfile
                ))
                .toList();
    }

    public Set<String> supportedProfiles() {
        return profileRules.keySet();
    }

    public static boolean isWholeDayWindow(LocalDateTime startTime, LocalDateTime endTime) {
        return startTime.toLocalTime().equals(java.time.LocalTime.MIDNIGHT)
                && endTime.toLocalTime().equals(java.time.LocalTime.MIDNIGHT);
    }

    public static String validateSchema(String schemaName) {
        if (schemaName == null || !IDENTIFIER.matcher(schemaName).matches()) {
            throw new IllegalArgumentException("账务 schema 只能包含字母、数字和下划线");
        }
        return schemaName;
    }

    private AccountingRuleDefinition transactionDuplicateRule() {
        return rule(
                "TX_DUPLICATE_SERIAL", "交易流水号重复", "交易", HOURLY, "ERROR",
                "同一小时窗口内 customer_transaction.serial_no 不应重复。",
                Set.of("customer_transaction"),
                (schema, start, end) -> {
                    String table = table(schema, "customer_transaction");
                    String where = window("create_time", start, end) + " AND is_delete = 0";
                    return sql(
                            """
                            SELECT COALESCE(SUM(t.duplicate_count - 1), 0) AS anomaly_count,
                                   CAST(0 AS DECIMAL(20,2)) AS difference_amount
                            FROM (
                              SELECT serial_no, COUNT(*) AS duplicate_count
                              FROM %s
                              WHERE %s
                                AND serial_no IS NOT NULL
                                AND serial_no <> ''
                              GROUP BY serial_no
                              HAVING COUNT(*) > 1
                            ) t
                            """.formatted(table, where),
                            """
                            SELECT serial_no AS business_key,
                                   COUNT(*) AS actual_count,
                                   1 AS expected_count
                            FROM %s
                            WHERE %s
                              AND serial_no IS NOT NULL
                              AND serial_no <> ''
                            GROUP BY serial_no
                            HAVING COUNT(*) > 1
                            ORDER BY actual_count DESC
                            LIMIT %d
                            """.formatted(table, where, SAMPLE_LIMIT)
                    );
                });
    }

    private AccountingRuleDefinition transactionAmountRule() {
        return rule(
                "TX_INVALID_AMOUNT", "交易金额异常", "交易", HOURLY, "ERROR",
                "交易金额为空或小于零；退款类流水应进入独立退款表。",
                Set.of("customer_transaction"),
                (schema, start, end) -> simpleAmountRule(
                        table(schema, "customer_transaction"), "create_time", "serial_no",
                        "transaction_amount", start, end));
    }

    private AccountingRuleDefinition pendTransactionAmountRule() {
        return rule(
                "PEND_INVALID_AMOUNT", "待清算流水金额异常", "清算", HOURLY, "ERROR",
                "待清算流水金额为空或小于零。",
                Set.of("pend_transaction"),
                (schema, start, end) -> simpleAmountRule(
                        table(schema, "pend_transaction"), "create_time", "serial_no",
                        "transaction_amount", start, end));
    }

    private AccountingRuleDefinition statementResourceRule() {
        return rule(
                "STATEMENT_ORPHAN_RESOURCE", "对账单资源缺少主单", "对账单", HOURLY, "ERROR",
                "statement_resource 必须能通过 statement_no 关联到 statement。",
                Set.of("statement", "statement_resource"),
                (schema, start, end) -> {
                    String statement = table(schema, "statement");
                    String resource = table(schema, "statement_resource");
                    String where = window("r.create_time", start, end) + " AND r.is_delete = 0";
                    return sql(
                            """
                            SELECT COUNT(*) AS anomaly_count,
                                   CAST(0 AS DECIMAL(20,2)) AS difference_amount
                            FROM %s r
                            LEFT JOIN %s s
                              ON s.statement_no = r.statement_no
                             AND s.is_delete = 0
                            WHERE %s
                              AND s.id IS NULL
                            """.formatted(resource, statement, where),
                            """
                            SELECT r.statement_no AS business_key,
                                   r.bill_no
                            FROM %s r
                            LEFT JOIN %s s
                              ON s.statement_no = r.statement_no
                             AND s.is_delete = 0
                            WHERE %s
                              AND s.id IS NULL
                            ORDER BY r.id DESC
                            LIMIT %d
                            """.formatted(resource, statement, where, SAMPLE_LIMIT)
                    );
                });
    }

    private AccountingRuleDefinition paySubjectAmountRule() {
        return rule(
                "PAY_SUBJECT_AMOUNT_MISMATCH", "支付明细与科目明细不一致", "支付", HOURLY, "ERROR",
                "repayment_plan_pay_detail.pay_amount 应等于对应科目明细 paid_amount 汇总。",
                Set.of("repayment_plan_pay_detail", "repayment_plan_pay_subject_detail"),
                (schema, start, end) -> {
                    String detail = table(schema, "repayment_plan_pay_detail");
                    String subject = table(schema, "repayment_plan_pay_subject_detail");
                    String source = """
                            SELECT d.serial_no,
                                   d.bill_no,
                                   d.pay_amount,
                                   COALESCE(SUM(s.paid_amount), 0) AS subject_amount
                            FROM %s d
                            LEFT JOIN %s s
                              ON s.pay_detail_serial_no = d.serial_no
                             AND s.is_delete = 0
                            WHERE %s
                              AND d.is_delete = 0
                            GROUP BY d.serial_no, d.bill_no, d.pay_amount
                            HAVING ABS(d.pay_amount - COALESCE(SUM(s.paid_amount), 0)) > 0.01
                            """.formatted(detail, subject, window("d.create_time", start, end));
                    return sql(
                            """
                            SELECT COUNT(*) AS anomaly_count,
                                   COALESCE(SUM(ABS(t.pay_amount - t.subject_amount)), 0) AS difference_amount
                            FROM (%s) t
                            """.formatted(source),
                            """
                            SELECT serial_no AS business_key,
                                   bill_no,
                                   pay_amount AS actual_amount,
                                   subject_amount AS expected_amount,
                                   pay_amount - subject_amount AS difference_amount
                            FROM (%s) t
                            ORDER BY ABS(pay_amount - subject_amount) DESC
                            LIMIT %d
                            """.formatted(source, SAMPLE_LIMIT)
                    );
                });
    }

    private AccountingRuleDefinition repaymentPlanAmountRule() {
        return rule(
                "PLAN_INVALID_AMOUNT", "账单金额字段异常", "账单", DAILY, "ERROR",
                "账单总额、已付、未付不得为空或为负，红冲金额按空值为零处理；不使用跨系统统一金额恒等式。",
                Set.of("repayment_plan"),
                (schema, start, end) -> {
                    String table = table(schema, "repayment_plan");
                    String condition = """
                            total_amount IS NULL OR paid_amount IS NULL OR unpaid_amount IS NULL
                            OR total_amount < 0 OR paid_amount < 0 OR unpaid_amount < 0
                            OR COALESCE(red_amount, 0) < 0
                            """;
                    return dailyConditionRule(table, "bill_date", "bill_no", condition, start, end);
                });
    }

    private AccountingRuleDefinition settledPlanRule() {
        return rule(
                "PLAN_SETTLED_WITH_UNPAID", "已结算账单仍有未付金额", "账单", DAILY, "WARN",
                "结算状态为已结算时，未付金额应为零且结算时间应存在。",
                Set.of("repayment_plan"),
                (schema, start, end) -> {
                    String table = table(schema, "repayment_plan");
                    String condition = "settlement_status = 1 AND (ABS(COALESCE(unpaid_amount, 0)) > 0.01 OR settlement_time IS NULL)";
                    return dailyConditionRule(table, "bill_date", "bill_no", condition, start, end);
                });
    }

    private AccountingRuleDefinition statementAmountRule() {
        return rule(
                "STATEMENT_INVALID_AMOUNT", "对账单金额字段异常", "对账单", DAILY, "ERROR",
                "对账单总额、已付、未付不得为空或为负，红冲金额按空值为零处理。",
                Set.of("statement"),
                (schema, start, end) -> {
                    String table = table(schema, "statement");
                    String condition = """
                            total_amount IS NULL OR paid_amount IS NULL OR unpaid_amount IS NULL
                            OR total_amount < 0 OR paid_amount < 0 OR unpaid_amount < 0
                            OR COALESCE(red_amount, 0) < 0
                            """;
                    return dailyConditionRule(table, "bill_date", "statement_no", condition, start, end);
                });
    }

    private AccountingRuleDefinition assetsStatementCompareRule() {
        return rule(
                "ASSETS_STATEMENT_COMPARE_ERROR", "资方与平台对账单比较异常", "资方对账", HOURLY, "ERROR",
                "复用 assets-saps 已落库的 statement_compare_result 结构化结果。",
                Set.of("statement_compare_result"),
                (schema, start, end) -> {
                    String table = table(schema, "statement_compare_result");
                    String where = window("create_time", start, end) + " AND is_delete = 0 AND compare_result = 1";
                    return sql(
                            """
                            SELECT COUNT(*) AS anomaly_count,
                                   COALESCE(SUM(ABS(COALESCE(capital_statement_amount, 0) - COALESCE(platform_statement_amount, 0))), 0) AS difference_amount
                            FROM %s
                            WHERE %s
                            """.formatted(table, where),
                            """
                            SELECT compare_no AS business_key,
                                   statement_no,
                                   capital_statement_no,
                                   capital_statement_amount AS actual_amount,
                                   platform_statement_amount AS expected_amount,
                                   result_desc AS message
                            FROM %s
                            WHERE %s
                            ORDER BY id DESC
                            LIMIT %d
                            """.formatted(table, where, SAMPLE_LIMIT)
                    );
                });
    }

    private AccountingRuleDefinition assetsChannelReconciliationRule() {
        return rule(
                "ASSETS_CHANNEL_RECON_MISMATCH", "渠道对账金额或笔数不一致", "渠道对账", DAILY, "ERROR",
                "内部金额、渠道金额和交易笔数应一致。",
                Set.of("channel_reconciliation"),
                (schema, start, end) -> {
                    String table = table(schema, "channel_reconciliation");
                    String condition = """
                            ABS(COALESCE(transaction_amount, 0) - COALESCE(channel_amount, 0)) > 0.01
                            OR ABS(COALESCE(account_amount, 0) - COALESCE(channel_account_amount, 0)) > 0.01
                            OR COALESCE(transaction_count, 0) <> COALESCE(channel_count, 0)
                            """;
                    return dailyConditionRule(table, "recon_date", "id", condition, start, end);
                });
    }

    private AccountingRuleDefinition ampRefactoringRequiredFieldsRule() {
        return rule(
                "AMP_REFACTORING_REQUIRED_FIELDS", "再保理账单资方字段缺失", "再保理", DAILY, "ERROR",
                "再保理账单必须具备 funder_bill_no 和 sec_funding_code。",
                Set.of("repayment_plan"),
                (schema, start, end) -> {
                    String table = table(schema, "repayment_plan");
                    String condition = """
                            asset_handle_type = 3
                            AND (funder_bill_no IS NULL OR funder_bill_no = ''
                                 OR sec_funding_code IS NULL OR sec_funding_code = '')
                            """;
                    return dailyConditionRule(table, "bill_date", "bill_no", condition, start, end);
                });
    }

    private AccountingRuleDefinition logisticsOrderTradeRule() {
        return rule(
                "LOGISTICS_ORDER_TRADE_MISMATCH", "订单与成功交易金额不一致", "物流交易", HOURLY, "ERROR",
                "成功订单的交易金额加优惠金额应等于成功交易汇总。",
                Set.of("customer_order", "customer_transaction"),
                (schema, start, end) -> {
                    String orders = table(schema, "customer_order");
                    String transactions = table(schema, "customer_transaction");
                    String source = """
                            SELECT o.id,
                                   o.order_no,
                                   o.transaction_amount + o.discount_amount AS order_amount,
                                   COALESCE(t.trade_amount, 0) AS trade_amount
                            FROM %s o
                            LEFT JOIN (
                              SELECT order_id, SUM(transaction_amount) AS trade_amount
                              FROM %s
                              WHERE is_delete = 0 AND status = 4
                              GROUP BY order_id
                            ) t ON t.order_id = o.id
                            WHERE %s
                              AND o.status = 4
                              AND o.is_delete = 0
                              AND ABS(o.transaction_amount + o.discount_amount - COALESCE(t.trade_amount, 0)) > 0.01
                            """.formatted(orders, transactions, window("o.create_time", start, end));
                    return aggregateDifferenceSql(source, "order_amount", "trade_amount", "order_no");
                });
    }

    private AccountingRuleDefinition logisticsBillPayDetailRule() {
        return rule(
                "LOGISTICS_BILL_PAY_DETAIL_MISMATCH", "账单已付金额与支付明细不一致", "物流账单", DAILY, "ERROR",
                "customer_bill.paid_amount 应等于 customer_bill_pay_detail.pay_amount 汇总。",
                Set.of("customer_bill", "customer_bill_pay_detail"),
                (schema, start, end) -> {
                    String bill = table(schema, "customer_bill");
                    String detail = table(schema, "customer_bill_pay_detail");
                    String source = """
                            SELECT b.id,
                                   b.bill_no,
                                   b.paid_amount,
                                   COALESCE(d.detail_amount, 0) AS detail_amount
                            FROM %s b
                            LEFT JOIN (
                              SELECT cb_id, SUM(pay_amount) AS detail_amount
                              FROM %s
                              WHERE is_delete = 0
                              GROUP BY cb_id
                            ) d ON d.cb_id = b.id
                            WHERE %s
                              AND b.is_delete = 0
                              AND ABS(b.paid_amount - COALESCE(d.detail_amount, 0)) > 0.01
                            """.formatted(bill, detail, dateWindow("b.bill_date", start, end));
                    return aggregateDifferenceSql(source, "paid_amount", "detail_amount", "bill_no");
                });
    }

    private AccountingRuleDefinition logisticsPaySubjectDetailRule() {
        return rule(
                "LOGISTICS_PAY_SUBJECT_MISMATCH", "支付明细与支付科目不一致", "物流支付", HOURLY, "ERROR",
                "customer_bill_pay_detail.pay_amount 应等于支付科目明细汇总。",
                Set.of("customer_bill_pay_detail", "customer_bill_pay_subject_detail"),
                (schema, start, end) -> {
                    String detail = table(schema, "customer_bill_pay_detail");
                    String subject = table(schema, "customer_bill_pay_subject_detail");
                    String source = """
                            SELECT d.id,
                                   d.pay_amount,
                                   COALESCE(s.subject_amount, 0) AS subject_amount
                            FROM %s d
                            LEFT JOIN (
                              SELECT cbpd_id, SUM(pay_amount) AS subject_amount
                              FROM %s
                              WHERE is_delete = 0
                              GROUP BY cbpd_id
                            ) s ON s.cbpd_id = d.id
                            WHERE %s
                              AND d.is_delete = 0
                              AND ABS(d.pay_amount - COALESCE(s.subject_amount, 0)) > 0.01
                            """.formatted(detail, subject, window("d.create_time", start, end));
                    return aggregateDifferenceSql(source, "pay_amount", "subject_amount", "id");
                });
    }

    private AccountingRuleSql simpleAmountRule(String table,
                                               String timeField,
                                               String keyField,
                                               String amountField,
                                               LocalDateTime start,
                                               LocalDateTime end) {
        String where = window(timeField, start, end)
                + " AND is_delete = 0 AND (" + amountField + " IS NULL OR " + amountField + " < 0)";
        return sql(
                """
                SELECT COUNT(*) AS anomaly_count,
                       COALESCE(SUM(ABS(COALESCE(%s, 0))), 0) AS difference_amount
                FROM %s
                WHERE %s
                """.formatted(amountField, table, where),
                """
                SELECT %s AS business_key,
                       %s AS actual_amount
                FROM %s
                WHERE %s
                ORDER BY id DESC
                LIMIT %d
                """.formatted(keyField, amountField, table, where, SAMPLE_LIMIT)
        );
    }

    private AccountingRuleSql dailyConditionRule(String table,
                                                 String dateField,
                                                 String keyField,
                                                 String condition,
                                                 LocalDateTime start,
                                                 LocalDateTime end) {
        String where = dateWindow(dateField, start, end) + " AND is_delete = 0 AND (" + condition + ")";
        return sql(
                """
                SELECT COUNT(*) AS anomaly_count,
                       CAST(0 AS DECIMAL(20,2)) AS difference_amount
                FROM %s
                WHERE %s
                """.formatted(table, where),
                """
                SELECT %s AS business_key
                FROM %s
                WHERE %s
                ORDER BY id DESC
                LIMIT %d
                """.formatted(keyField, table, where, SAMPLE_LIMIT)
        );
    }

    private AccountingRuleSql aggregateDifferenceSql(String source,
                                                     String actualField,
                                                     String expectedField,
                                                     String keyField) {
        return sql(
                """
                SELECT COUNT(*) AS anomaly_count,
                       COALESCE(SUM(ABS(t.%s - t.%s)), 0) AS difference_amount
                FROM (%s) t
                """.formatted(actualField, expectedField, source),
                """
                SELECT %s AS business_key,
                       %s AS actual_amount,
                       %s AS expected_amount,
                       %s - %s AS difference_amount
                FROM (%s) t
                ORDER BY ABS(%s - %s) DESC
                LIMIT %d
                """.formatted(
                        keyField, actualField, expectedField, actualField, expectedField,
                        source, actualField, expectedField, SAMPLE_LIMIT)
        );
    }

    private AccountingRuleDefinition rule(String code,
                                          String name,
                                          String category,
                                          String granularity,
                                          String severity,
                                          String description,
                                          Set<String> requiredTables,
                                          AccountingRuleDefinition.SqlBuilder sqlBuilder) {
        return new AccountingRuleDefinition(
                code, name, category, granularity, severity, description,
                Set.copyOf(requiredTables), sqlBuilder);
    }

    private AccountingRuleSql sql(String summary, String sample) {
        return new AccountingRuleSql(summary.strip(), sample.strip());
    }

    private String table(String schemaName, String tableName) {
        String schema = validateSchema(schemaName);
        if (!IDENTIFIER.matcher(tableName).matches()) {
            throw new IllegalArgumentException("非法账务表名");
        }
        return "`" + schema + "`.`" + tableName + "`";
    }

    private String window(String field, LocalDateTime start, LocalDateTime end) {
        return field + " >= '" + DATE_TIME.format(start) + "' AND "
                + field + " < '" + DATE_TIME.format(end) + "'";
    }

    private String dateWindow(String field, LocalDateTime start, LocalDateTime end) {
        return field + " >= '" + start.toLocalDate() + "' AND "
                + field + " < '" + end.toLocalDate() + "'";
    }

    @SafeVarargs
    private final List<AccountingRuleDefinition> append(List<AccountingRuleDefinition> base,
                                                        AccountingRuleDefinition... additions) {
        List<AccountingRuleDefinition> result = new ArrayList<>(base);
        result.addAll(List.of(additions));
        return List.copyOf(result);
    }
}
