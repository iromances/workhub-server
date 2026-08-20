package cn.aslight.workhub.mcp.security;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MCP 数据库查询结果脱敏器。优先按真实列名识别，列名不足时仅按高置信度值格式兜底。
 */
public class SensitiveDataMasker {

    private static final Pattern MOBILE_PATTERN = Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("(?<![0-9A-Za-z])([1-9]\\d{5}(?:19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx])(?![0-9A-Za-z])");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("(?i)([A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,})");
    private static final Pattern IPV4_PATTERN = Pattern.compile("^(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\.\\d{1,3}$");
    private static final Pattern SQL_LITERAL_PATTERN = Pattern.compile(
            "(?i)([A-Z0-9_`.]+)\\s*(=|<>|!=|LIKE)\\s*('(?:''|[^'])*'|\\\"(?:\\\"\\\"|[^\\\"])*\\\"|[0-9]+)"
    );
    private static final Pattern SQL_QUOTED_LITERAL_PATTERN = Pattern.compile(
            "'(?:''|[^'])*'|\\\"(?:\\\"\\\"|[^\\\"])*\\\""
    );

    private static final Set<String> NON_VALUE_SUFFIXES = Set.of(
            "status", "type", "flag", "count", "enabled", "verified", "source"
    );

    public Object mask(String columnLabel, String sourceColumnName, Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        String value = String.valueOf(rawValue);
        SensitiveType type = classify(columnLabel, sourceColumnName);
        if (type == SensitiveType.NONE) {
            String masked = maskHighConfidenceValue(value);
            return masked.equals(value) ? rawValue : masked;
        }
        return switch (type) {
            case SECRET -> "******";
            case MOBILE -> maskMobile(value);
            case ID_CARD -> maskIdentity(value);
            case BANK_CARD -> maskSuffix(value, 4);
            case EMAIL -> maskEmail(value);
            case PERSON_NAME -> maskName(value);
            case ADDRESS -> maskAddress(value);
            case BIRTH_DATE -> "****-**-**";
            case IP_ADDRESS -> maskIp(value);
            case LICENSE_PLATE -> maskSuffix(value, 2);
            case NONE -> rawValue;
        };
    }

    public String maskSql(String sql) {
        if (sql == null || sql.isBlank()) {
            return sql;
        }
        Matcher matcher = SQL_LITERAL_PATTERN.matcher(sql);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            SensitiveType type = classify(matcher.group(1), matcher.group(1));
            if (type == SensitiveType.NONE) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(maskHighConfidenceText(matcher.group())));
                continue;
            }
            String replacement = matcher.group(1) + " " + matcher.group(2) + " '***'";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        String withoutStringLiterals = SQL_QUOTED_LITERAL_PATTERN.matcher(result.toString()).replaceAll("'***'");
        return maskHighConfidenceText(withoutStringLiterals);
    }

    public String maskText(String value) {
        return value == null ? null : maskHighConfidenceText(value);
    }

    private SensitiveType classify(String columnLabel, String sourceColumnName) {
        String name = normalize(columnLabel) + " " + normalize(sourceColumnName);
        if (hasNonValueSuffix(name)) {
            return SensitiveType.NONE;
        }
        if (containsAny(name, "password", "passwd", "pwd", "secret", "token", "api_key", "access_key",
                "private_key", "credential", "encrypted_value", "plain_value", "密码", "密钥", "令牌")) {
            return SensitiveType.SECRET;
        }
        if (containsAny(name, "id_card", "idcard", "identity_no", "identity_number", "cert_no",
                "certificate_no", "credential_no", "citizen_id", "national_id", "id_no", "证件号", "身份证")) {
            return SensitiveType.ID_CARD;
        }
        if (containsAny(name, "mobile", "phone", "telephone", "tel_no", "reserved_tel", "手机号", "联系电话")) {
            return SensitiveType.MOBILE;
        }
        if (containsAny(name, "bank_card", "card_no", "card_number", "account_no", "account_number",
                "bank_account", "银行卡", "银行账号")) {
            return SensitiveType.BANK_CARD;
        }
        if (containsAny(name, "email", "mail_address", "邮箱")) {
            return SensitiveType.EMAIL;
        }
        if (containsAny(name, "real_name", "full_name", "person_name", "customer_name", "contact_name",
                "user_name", "username", "legal_person", "applicant_name", "borrower_name", "holder_name",
                "owner_name", "姓名", "联系人")) {
            return SensitiveType.PERSON_NAME;
        }
        if (containsAny(name, "address", "contact_addr", "home_addr", "residence", "住址", "地址")) {
            return SensitiveType.ADDRESS;
        }
        if (containsAny(name, "birthday", "birth_date", "date_of_birth", "出生日期")) {
            return SensitiveType.BIRTH_DATE;
        }
        if (containsAny(name, "ip_address", "client_ip", "remote_ip", "ip_addr")) {
            return SensitiveType.IP_ADDRESS;
        }
        if (containsAny(name, "license_plate", "plate_no", "vehicle_no", "车牌")) {
            return SensitiveType.LICENSE_PLATE;
        }
        return SensitiveType.NONE;
    }

    private String maskHighConfidenceValue(String value) {
        if (MOBILE_PATTERN.matcher(value).matches()) {
            return maskMobile(value);
        }
        if (ID_CARD_PATTERN.matcher(value).matches()) {
            return maskIdentity(value);
        }
        if (EMAIL_PATTERN.matcher(value).matches()) {
            return maskEmail(value);
        }
        return value;
    }

    private String maskHighConfidenceText(String value) {
        String masked = replaceMatches(value, MOBILE_PATTERN, this::maskMobile);
        masked = replaceMatches(masked, ID_CARD_PATTERN, this::maskIdentity);
        return replaceMatches(masked, EMAIL_PATTERN, this::maskEmail);
    }

    private String replaceMatches(String value, Pattern pattern, java.util.function.Function<String, String> masker) {
        Matcher matcher = pattern.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(masker.apply(matcher.group(1))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String maskMobile(String value) {
        if (value.length() < 7) {
            return "***";
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    private String maskIdentity(String value) {
        if (value.length() < 8) {
            return "***";
        }
        return value.substring(0, 3) + "*".repeat(Math.max(4, value.length() - 7))
                + value.substring(value.length() - 4);
    }

    private String maskEmail(String value) {
        int at = value.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return value.charAt(0) + "***" + value.substring(at);
    }

    private String maskName(String value) {
        if (value.isBlank()) {
            return value;
        }
        int firstCodePoint = value.codePointAt(0);
        return new String(Character.toChars(firstCodePoint)) + "**";
    }

    private String maskAddress(String value) {
        if (value.length() <= 3) {
            return "***";
        }
        return value.substring(0, Math.min(3, value.length())) + "***";
    }

    private String maskIp(String value) {
        Matcher matcher = IPV4_PATTERN.matcher(value);
        return matcher.matches() ? matcher.group(1) + ".***" : "***";
    }

    private String maskSuffix(String value, int visibleSuffix) {
        if (value.length() <= visibleSuffix) {
            return "***";
        }
        return "*".repeat(Math.min(12, value.length() - visibleSuffix))
                + value.substring(value.length() - visibleSuffix);
    }

    private boolean hasNonValueSuffix(String normalizedName) {
        for (String suffix : NON_VALUE_SUFFIXES) {
            if (normalizedName.contains("_" + suffix) || normalizedName.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replace('`', ' ')
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private enum SensitiveType {
        NONE,
        SECRET,
        MOBILE,
        ID_CARD,
        BANK_CARD,
        EMAIL,
        PERSON_NAME,
        ADDRESS,
        BIRTH_DATE,
        IP_ADDRESS,
        LICENSE_PLATE
    }
}
