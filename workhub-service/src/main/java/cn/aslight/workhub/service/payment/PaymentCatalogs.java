package cn.aslight.workhub.service.payment;

import cn.aslight.workhub.model.payment.PaymentPurposeOptionResponse;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 支付域字典。
 */
public final class PaymentCatalogs {

    static final Set<String> ACTIVE_STATUSES = Set.of("ACTIVE", "INACTIVE");
    static final Set<String> ENVIRONMENTS = Set.of("PROD", "UAT", "SIT", "TEST");
    static final Set<String> VALUE_TYPES = Set.of("TEXT", "JSON", "URL", "NUMBER", "CERT", "PEM");
    static final Set<String> SECRET_TYPES = Set.of(
            "API_KEY",
            "API_SECRET",
            "PRIVATE_KEY",
            "PUBLIC_KEY",
            "CERTIFICATE",
            "CERT_PASSWORD",
            "SIGN_SECRET",
            "APP_SECRET"
    );

    private static final Map<String, PaymentPurposeOptionResponse> PURPOSES = Map.ofEntries(
            Map.entry("BIND_CARD", new PaymentPurposeOptionResponse("BIND_CARD", "绑卡", "银行卡签约、绑卡、预绑卡等场景")),
            Map.entry("UNBIND_CARD", new PaymentPurposeOptionResponse("UNBIND_CARD", "解绑", "解绑银行卡、解约")),
            Map.entry("WITHHOLD", new PaymentPurposeOptionResponse("WITHHOLD", "代收", "委托扣款、代扣、代收")),
            Map.entry("PAY_OUT", new PaymentPurposeOptionResponse("PAY_OUT", "代付", "出款、代付、放款")),
            Map.entry("SPLIT_SETTLEMENT", new PaymentPurposeOptionResponse("SPLIT_SETTLEMENT", "分账", "分账、分润、清分")),
            Map.entry("REFUND", new PaymentPurposeOptionResponse("REFUND", "退款", "原路退回、退票、冲正")),
            Map.entry("QUERY_ORDER", new PaymentPurposeOptionResponse("QUERY_ORDER", "订单查询", "订单查询、支付结果查询")),
            Map.entry("DOWNLOAD_RECON", new PaymentPurposeOptionResponse("DOWNLOAD_RECON", "对账下载", "账单、对账文件下载")),
            Map.entry("SIGN_AGREEMENT", new PaymentPurposeOptionResponse("SIGN_AGREEMENT", "签约", "开户、签约、协议确认")),
            Map.entry("VERIFY_ACCOUNT", new PaymentPurposeOptionResponse("VERIFY_ACCOUNT", "账户校验", "实名认证、银行卡校验")),
            Map.entry("RECHARGE", new PaymentPurposeOptionResponse("RECHARGE", "充值", "账户充值")),
            Map.entry("WITHDRAW", new PaymentPurposeOptionResponse("WITHDRAW", "提现", "提现、出金")),
            Map.entry("TRANSFER", new PaymentPurposeOptionResponse("TRANSFER", "转账", "转账、划拨")),
            Map.entry("BALANCE_QUERY", new PaymentPurposeOptionResponse("BALANCE_QUERY", "余额查询", "账户余额查询")),
            Map.entry("CALLBACK_VERIFY", new PaymentPurposeOptionResponse("CALLBACK_VERIFY", "回调验签", "回调报文验签、通知验签"))
    );

    private PaymentCatalogs() {
    }

    public static List<PaymentPurposeOptionResponse> listPurposes() {
        return PURPOSES.values().stream().sorted((left, right) -> left.code().compareTo(right.code())).toList();
    }

    public static String normalizeStatus(String value, String fieldName) {
        return ensureAllowed(uppercase(value), ACTIVE_STATUSES, fieldName + " 不支持");
    }

    public static String normalizeEnvironment(String value) {
        return ensureAllowed(uppercase(value), ENVIRONMENTS, "environment 不支持");
    }

    public static String normalizePurpose(String value) {
        return ensureAllowed(uppercase(value), PURPOSES.keySet(), "purposeCode 不支持");
    }

    public static String normalizeValueType(String value) {
        return ensureAllowed(uppercase(value), VALUE_TYPES, "valueType 不支持");
    }

    public static String normalizeSecretType(String value) {
        return ensureAllowed(uppercase(value), SECRET_TYPES, "secretType 不支持");
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String requireText(String value, String fieldName) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return trimmed;
    }

    public static int requirePositivePriority(Integer priority) {
        if (priority == null || priority < 1) {
            throw new IllegalArgumentException("priority 必须大于 0");
        }
        return priority;
    }

    private static String uppercase(String value) {
        String trimmed = requireText(value, "参数");
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private static String ensureAllowed(String value, Set<String> allowed, String message) {
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
