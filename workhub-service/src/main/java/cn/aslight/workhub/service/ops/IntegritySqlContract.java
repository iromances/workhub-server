package cn.aslight.workhub.service.ops;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * REQ-7f26a063 §3.5：OPS_ACCOUNTING_INTEGRITY 模板的保存契约。
 * 编码必须以 _BOUNDS/_SUMMARY/_SAMPLE 结尾，并按下表校验参数引用与必需文本；
 * 既有通用校验（只读前缀、:name 占位、长度）不在此重复。
 */
public final class IntegritySqlContract {

    private static final Pattern PARAM = Pattern.compile(":([A-Za-z_][A-Za-z0-9_]*)");

    private static final Set<String> SUMMARY_PARAMS = Set.of(
            "startId", "endId", "startDateTime", "endExclusiveDateTime", "legacyMaxId");
    private static final Set<String> SAMPLE_PARAMS = Set.of(
            "startId", "endId", "startDateTime", "endExclusiveDateTime", "legacyMaxId", "ruleCode", "sampleLimit");

    private IntegritySqlContract() {
    }

    public static void validate(String sqlCode, String sqlTemplate) {
        String kind = suffixOf(sqlCode);
        Set<String> used = paramsOf(sqlTemplate);
        switch (kind) {
            case "BOUNDS" -> {
                if (!used.isEmpty()) {
                    throw new IllegalArgumentException("边界模板不允许引用参数：" + String.join("、", used));
                }
            }
            case "SUMMARY" -> {
                requireParam(used, "startId", "汇总模板缺少必需参数 :startId");
                requireParam(used, "endId", "汇总模板缺少必需参数 :endId");
                rejectUnknown(used, SUMMARY_PARAMS, "汇总模板");
                if (!sqlTemplate.contains("checked_count")) {
                    throw new IllegalArgumentException("汇总模板必须输出 checked_count 计数列");
                }
            }
            case "SAMPLE" -> {
                requireParam(used, "startId", "样本模板缺少必需参数 :startId");
                requireParam(used, "endId", "样本模板缺少必需参数 :endId");
                requireParam(used, "ruleCode", "样本模板缺少必需参数 :ruleCode");
                requireParam(used, "sampleLimit", "样本模板缺少必需参数 :sampleLimit");
                rejectUnknown(used, SAMPLE_PARAMS, "样本模板");
            }
            default -> throw new IllegalArgumentException("一致性模板编码必须以 _BOUNDS/_SUMMARY/_SAMPLE 结尾：" + sqlCode);
        }
    }

    /** 编码后缀（BOUNDS/SUMMARY/SAMPLE）；不符合契约时返回空串由调用分支拒绝。 */
    public static String suffixOf(String sqlCode) {
        if (sqlCode == null) {
            return "";
        }
        if (sqlCode.endsWith("_BOUNDS")) return "BOUNDS";
        if (sqlCode.endsWith("_SUMMARY")) return "SUMMARY";
        if (sqlCode.endsWith("_SAMPLE")) return "SAMPLE";
        return "";
    }

    public static String sqlCode(String familyCode, String suffix) {
        return "AI_" + familyCode.toUpperCase(java.util.Locale.ROOT) + "_" + suffix;
    }

    private static void requireParam(Set<String> used, String name, String message) {
        if (!used.contains(name)) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void rejectUnknown(Set<String> used, Set<String> allowed, String label) {
        Set<String> unknown = new LinkedHashSet<>(used);
        unknown.removeAll(allowed);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(label + "引用了未传入的参数：" + String.join("、", unknown));
        }
    }

    private static Set<String> paramsOf(String template) {
        Set<String> names = new LinkedHashSet<>();
        if (template == null) {
            return names;
        }
        Matcher matcher = PARAM.matcher(template);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }
}
