package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.model.ops.AccountingScenarioDimensionResponse;
import cn.aslight.workhub.model.ops.AccountingScenarioTypeResponse;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * REQ-d227e09d：业务监测场景目录——五类业务场景统一三段式核对维度，
 * 结果行 rule_code = 场景类型_维度后缀（如 BATCH_WITHHOLD_EXPECTED_VS_ACTUAL）。
 */
public final class AccountingScenarioCatalog {

    public static final String BATCH_WITHHOLD = "BATCH_WITHHOLD";
    public static final String COMPENSATION = "COMPENSATION";
    public static final String BUYBACK = "BUYBACK";
    public static final String PAYMENT = "PAYMENT";
    public static final String CLEARING_SETTLE = "CLEARING_SETTLE";

    public static final String STARTED_ON_TIME = "STARTED_ON_TIME";
    public static final String EXPECTED_VS_ACTUAL = "EXPECTED_VS_ACTUAL";
    public static final String SUCCESS_RATE = "SUCCESS_RATE";

    private static final List<ScenarioType> TYPES = List.of(
            new ScenarioType(BATCH_WITHHOLD, "批量代扣", "每日定时批量发起账单扣款"),
            new ScenarioType(COMPENSATION, "代偿", "代偿扣款/入账流程"),
            new ScenarioType(BUYBACK, "回购", "回购流程"),
            new ScenarioType(PAYMENT, "支付", "支付流程"),
            new ScenarioType(CLEARING_SETTLE, "清分结算", "清分结算流程"),
            new ScenarioType(AccountingIntegrityCatalog.TYPE, "单据及支付一致性", "叉车、智能柜单据生成、明细总额、结算及渠道交叉核验；仅手动执行"));

    private AccountingScenarioCatalog() {
    }

    public static Set<String> supportedTypes() {
        return TYPES.stream().map(ScenarioType::type).collect(Collectors.toUnmodifiableSet());
    }

    public static List<AccountingScenarioTypeResponse> responses() {
        return TYPES.stream()
                .map(type -> new AccountingScenarioTypeResponse(
                        type.type(), type.name(), type.description(), dimensions(type)))
                .toList();
    }

    public static String typeName(String scenarioType) {
        return type(scenarioType).name();
    }

    public static String dimensionName(String dimensionCode) {
        return switch (dimensionCode) {
            case STARTED_ON_TIME -> "按时执行";
            case EXPECTED_VS_ACTUAL -> "应发起 vs 实际";
            case SUCCESS_RATE -> "实际 vs 成功";
            default -> throw new IllegalArgumentException("不支持的监测维度：" + dimensionCode);
        };
    }

    /** 结果行 rule_code：场景类型_维度后缀。 */
    public static String ruleCode(String scenarioType, String dimensionCode) {
        requireType(scenarioType);
        dimensionName(dimensionCode);
        return scenarioType + "_" + dimensionCode;
    }

    public static String requireType(String scenarioType) {
        return type(scenarioType).type();
    }

    private static ScenarioType type(String scenarioType) {
        return TYPES.stream()
                .filter(type -> type.type().equals(scenarioType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的业务监测场景类型：" + scenarioType));
    }

    private static List<AccountingScenarioDimensionResponse> dimensions(ScenarioType type) {
        if (AccountingIntegrityCatalog.TYPE.equals(type.type())) {
            return List.of(new AccountingScenarioDimensionResponse("INTEGRITY", "单据及支付一致性",
                    "日期可选，默认全量分批；展示异常数、绝对差额及样本；明细缺口单独提示"));
        }
        return List.of(
                new AccountingScenarioDimensionResponse(STARTED_ON_TIME, "按时执行",
                        "核对时间已过但当日" + type.name() + "未发起任何记录时告警"),
                new AccountingScenarioDimensionResponse(EXPECTED_VS_ACTUAL, "应发起 vs 实际",
                        "应发起笔数与实际发起笔数差异超出容差时告警"),
                new AccountingScenarioDimensionResponse(SUCCESS_RATE, "实际 vs 成功",
                        "发起与成功笔数差异超出容差时告警；未配置成功 SQL 则跳过"));
    }

    private record ScenarioType(String type, String name, String description) {
    }
}
