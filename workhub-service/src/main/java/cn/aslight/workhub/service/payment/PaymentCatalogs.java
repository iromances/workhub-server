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
    static final Set<String> SECRET_FILE_VALUE_TYPES = Set.of("TEXT", "BINARY");
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
    static final Set<String> RELATION_ROLES = Set.of(
            "MAIN",
            "SHARED_AGREEMENT",
            "NORMAL_SPLIT",
            "COMPENSATION_REPURCHASE",
            "SPLIT_RECEIVER",
            "REFUND_MAIN",
            "SUB_MERCHANT"
    );
    static final Set<String> CREDENTIAL_TYPES = Set.of(
            "USERNAME",
            "PASSWORD",
            "TRADE_PASSWORD",
            "AES_KEY",
            "SIGN_KEY",
            "PRIVATE_KEY_PASSWORD",
            "CERT_PASSWORD",
            "TOKEN",
            "OTHER_SECRET"
    );

    private static final Map<String, PaymentPurposeOptionResponse> PURPOSES = Map.ofEntries(
            Map.entry("BIND_CARD", new PaymentPurposeOptionResponse("BIND_CARD", "银行卡签约", "银行卡签约、绑卡、预绑卡等场景")),
            Map.entry("WITHHOLD", new PaymentPurposeOptionResponse("WITHHOLD", "代收", "委托扣款、代扣、代收")),
            Map.entry("REFUND", new PaymentPurposeOptionResponse("REFUND", "退款", "原路退回、退票、冲正")),
            Map.entry("WITHDRAW", new PaymentPurposeOptionResponse("WITHDRAW", "提现", "提现、出金")),
            Map.entry("TRANSFER", new PaymentPurposeOptionResponse("TRANSFER", "转账", "转账、划拨")),
            Map.entry("WITHHOLD_SPLIT_SETTLEMENT", new PaymentPurposeOptionResponse("WITHHOLD_SPLIT_SETTLEMENT", "代收分账", "代收后按规则分账、清分")),
            Map.entry("ACCOUNT_SYSTEM", new PaymentPurposeOptionResponse("ACCOUNT_SYSTEM", "账户体系", "支付渠道账户体系能力"))
    );
    private static final Map<String, String> LEGACY_PURPOSE_ALIASES = Map.ofEntries(
            Map.entry("WITHHOLD_SUB_MERCHANT", "WITHHOLD"),
            Map.entry("WITHHOLD_SUB_MERCHANT_BEIJING", "WITHHOLD"),
            Map.entry("WITHHOLD_SUB_MERCHANT_TIANJIN", "WITHHOLD"),
            Map.entry("WITHHOLD_SUB_MERCHANT_PROD_TEST", "WITHHOLD"),
            Map.entry("SIGN_AGREEMENT", "BIND_CARD"),
            Map.entry("PAY_OUT", "TRANSFER"),
            Map.entry("SPLIT_SETTLEMENT", "WITHHOLD_SPLIT_SETTLEMENT"),
            Map.entry("SPLIT_RECEIVER_XXT", "WITHHOLD_SPLIT_SETTLEMENT"),
            Map.entry("SPLIT_RECEIVER_LIYI", "WITHHOLD_SPLIT_SETTLEMENT"),
            Map.entry("REFUND_MAIN_ACCOUNT", "REFUND")
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
        String purposeCode = uppercase(value);
        return ensureAllowed(LEGACY_PURPOSE_ALIASES.getOrDefault(purposeCode, purposeCode), PURPOSES.keySet(), "purposeCode 不支持");
    }

    public static String normalizeValueType(String value) {
        return ensureAllowed(uppercase(value), VALUE_TYPES, "valueType 不支持");
    }

    public static String normalizeSecretType(String value) {
        return ensureAllowed(uppercase(value), SECRET_TYPES, "secretType 不支持");
    }

    public static String normalizeSecretFileValueType(String value) {
        return ensureAllowed(uppercase(value), SECRET_FILE_VALUE_TYPES, "fileValueType 不支持");
    }

    public static String normalizeRelationRole(String value) {
        return ensureAllowed(uppercase(value), RELATION_ROLES, "relationRole 不支持");
    }

    public static String normalizeCredentialType(String value) {
        return ensureAllowed(uppercase(value), CREDENTIAL_TYPES, "credentialType 不支持");
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
