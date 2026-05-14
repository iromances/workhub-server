package cn.aslight.workhub.service.intake;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 研发需求分支名生成器。
 */
final class DevelopmentBranchNameGenerator {

    private static final int MAX_BRANCH_WORDS = 3;

    private static final List<Map.Entry<String, String>> SUBJECT_TRANSLATIONS = List.of(
            Map.entry("里易二轮车", "liyi_ebike"),
            Map.entry("二轮车", "ebike"),
            Map.entry("沃橙", "wocheng"),
            Map.entry("嘉泰保理", "jiatai_factoring"),
            Map.entry("嘉泰", "jiatai"),
            Map.entry("易宝", "yeepay"),
            Map.entry("苏宁支付", "suning_pay")
    );

    private static final List<Map.Entry<String, String>> KEYWORD_TRANSLATIONS = List.of(
            Map.entry("分账", "split_account"),
            Map.entry("核验", "verify"),
            Map.entry("绑卡", "bind_card"),
            Map.entry("换电", "battery_swap"),
            Map.entry("银行卡", "bank_card"),
            Map.entry("接口", "api"),
            Map.entry("对接", "integration"),
            Map.entry("支付", "payment"),
            Map.entry("扣款", "withhold"),
            Map.entry("还款", "repayment"),
            Map.entry("放款", "loan"),
            Map.entry("取数", "data_export"),
            Map.entry("导出", "data_export"),
            Map.entry("数据", "data"),
            Map.entry("报表", "report"),
            Map.entry("审批", "approval"),
            Map.entry("商户", "merchant"),
            Map.entry("客户", "customer"),
            Map.entry("产品", "product"),
            Map.entry("校验", "validate"),
            Map.entry("规则", "rule"),
            Map.entry("配置", "config"),
            Map.entry("优化", "optimize"),
            Map.entry("改造", "refactor"),
            Map.entry("修复", "fix"),
            Map.entry("新增", "add")
    );

    private DevelopmentBranchNameGenerator() {
    }

    static String normalize(String existingBranchName,
                            String requirementType,
                            String approvalCode,
                            String submittedTime,
                            String requirementName,
                            String requirementDigest,
                            String requirementSummary) {
        if (!"研发需求".equals(trimToNull(requirementType))) {
            return null;
        }
        String textSource = firstNonBlank(requirementName, firstNonBlank(requirementDigest, requirementSummary));
        String dateToken = resolveDateToken(submittedTime, approvalCode);
        String normalizedExistingBranchName = normalizeExistingBranchName(existingBranchName, dateToken);
        if (normalizedExistingBranchName != null) {
            return normalizedExistingBranchName;
        }
        String generatedBranchName = buildGeneratedBranchName(textSource, dateToken);
        if (generatedBranchName != null) {
            return generatedBranchName;
        }
        return buildBranchName("requirement", dateToken);
    }

    private static String buildGeneratedBranchName(String textSource, String dateToken) {
        String subjectToken = buildSubject(textSource);
        String summaryToken = buildSummary(textSource);
        if (summaryToken == null) {
            return null;
        }
        String branchToken = compactBranchToken(subjectToken, summaryToken);
        return buildBranchName(branchToken, dateToken);
    }

    private static String normalizeExistingBranchName(String existingBranchName, String dateToken) {
        String normalized = trimToNull(existingBranchName);
        if (normalized == null) {
            return null;
        }
        if ("feature/requirement".equals(normalized) || "requirement".equals(normalized)) {
            return null;
        }
        return normalized;
    }

    private static String compactBranchToken(String subjectToken, String summaryToken) {
        LinkedHashSet<String> words = new LinkedHashSet<>();
        appendWords(words, subjectToken);
        appendWords(words, summaryToken);
        return firstWords(words, MAX_BRANCH_WORDS);
    }

    private static String buildBranchName(String branchToken, String dateToken) {
        if (dateToken == null) {
            return "feature/" + branchToken;
        }
        return "feature/" + branchToken + "_" + dateToken;
    }

    private static String buildSubject(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : SUBJECT_TRANSLATIONS) {
            if (normalized.contains(entry.getKey())) {
                return firstWords(toWords(entry.getValue()), 3);
            }
        }
        return null;
    }

    private static String buildSummary(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        LinkedHashSet<String> words = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : KEYWORD_TRANSLATIONS) {
            if (normalized.contains(entry.getKey())) {
                appendWords(words, entry.getValue());
                if (words.size() >= 3) {
                    return firstWords(words, 3);
                }
            }
        }
        String asciiToken = sanitizeAsciiToken(normalized);
        if (asciiToken != null) {
            for (String word : asciiToken.split("_")) {
                appendWords(words, word);
                if (words.size() >= 3) {
                    return firstWords(words, 3);
                }
            }
        }
        if (words.isEmpty()) {
            return null;
        }
        return firstWords(words, 3);
    }

    private static LinkedHashSet<String> toWords(String token) {
        LinkedHashSet<String> words = new LinkedHashSet<>();
        appendWords(words, token);
        return words;
    }

    private static void appendWords(LinkedHashSet<String> words, String token) {
        String normalized = trimToNull(token);
        if (normalized == null) {
            return;
        }
        for (String word : normalized.split("_")) {
            String safeWord = sanitizeAsciiToken(word);
            if (safeWord != null) {
                words.add(safeWord);
            }
        }
    }

    private static String firstWords(LinkedHashSet<String> words, int limit) {
        return words.stream().limit(limit).collect(java.util.stream.Collectors.joining("_"));
    }

    private static String resolveDateToken(String submittedTime, String approvalCode) {
        String fromSubmittedTime = formatDate(trimToNull(submittedTime));
        if (fromSubmittedTime != null) {
            return fromSubmittedTime;
        }
        String fromApprovalCode = firstRegexGroup(trimToNull(approvalCode), "(20\\d{6})");
        return fromApprovalCode == null ? null : fromApprovalCode;
    }

    private static String formatDate(String value) {
        if (value == null) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(\\d{4})\\D*(\\d{1,2})\\D*(\\d{1,2})")
                .matcher(value);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1) + padDatePart(matcher.group(2)) + padDatePart(matcher.group(3));
    }

    private static String firstRegexGroup(String value, String regex) {
        if (value == null) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(regex).matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String padDatePart(String value) {
        return value.length() == 1 ? "0" + value : value;
    }

    private static String sanitizeAsciiToken(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        String sanitized = normalized.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        sanitized = sanitized.replaceAll("^_+", "").replaceAll("_+$", "");
        return sanitized.isEmpty() ? null : sanitized;
    }

    private static String firstNonBlank(String first, String second) {
        String normalizedFirst = trimToNull(first);
        return normalizedFirst == null ? trimToNull(second) : normalizedFirst;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
