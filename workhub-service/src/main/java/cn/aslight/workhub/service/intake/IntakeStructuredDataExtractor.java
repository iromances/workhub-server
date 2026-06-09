package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.intake.IntakeAttachmentSummary;
import cn.aslight.workhub.model.intake.IntakeStructuredField;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 待整理结构化字段抽取服务。
 */
@Component
public class IntakeStructuredDataExtractor {

    public IntakeStructuredData extract(String rawContent) {
        String normalized = rawContent == null ? "" : rawContent.replace("\r", "").trim();
        if (normalized.isEmpty()) {
            return null;
        }

        List<IntakeStructuredField> fields = parseFields(normalized);
        if (fields.isEmpty()) {
            return null;
        }

        String approvalTitle = firstValue(fields, "审批标题", "标题");
        String proposerName = firstNonBlank(firstValue(fields, "提出人", "提报人", "申请人", "提交人"), extractProposerName(approvalTitle));
        String approvalCode = firstValue(fields, "审批编号", "单号", "审批单号");
        String submittedTime = firstValue(fields, "提交时间", "提报时间", "申请时间");
        String requirementType = firstNonBlank(firstValue(fields, "需求类型", "事项类型"), inferRequirementType(normalized));
        String requirementName = firstValue(fields, "需求名称", "需求标题");
        String requirementSummary = firstValue(fields, "需求简介", "需求描述", "需求说明");
        String requirementDigest = firstNonBlank(firstValue(fields, "需求摘要", "摘要"), buildRequirementDigest(requirementName, requirementSummary, approvalTitle));
        String department = firstValue(fields, "所在部门", "申请部门");
        String businessLine = firstValue(fields, "需求所属业务线", "业务线");
        String projectHint = firstValue(fields, "业务线", "所属业务线", "项目组", "所属项目组");
        String remark = firstValue(fields, "备注");
        String plannedDueDate = firstValue(fields, "预估完成时间", "计划完成时间", "预计完成时间");
        String developmentStartedDate = firstValue(fields, "研发开始日期", "开始研发时间", "研发开始时间");
        String actualEffort = EffortUnitNormalizer.normalizeEffort(firstValue(fields, "实际工时"));
        String testingStartedDate = firstValue(fields, "测试开始日期", "开始测试时间", "测试开始时间");
        String actualCompletedTime = firstValue(fields, "实际完成时间");
        String acceptanceTime = firstValue(fields, "验收时间", "实际验收完成时间", "实际验收时间", "验收完成时间");
        projectHint = firstNonBlank(projectHint, firstNonBlank(businessLine, department));
        String category = requirementName != null || requirementSummary != null ? "需求审批" : "待整理项";

        return new IntakeStructuredData(
                category,
                approvalTitle,
                proposerName,
                null,
                approvalCode,
                submittedTime,
                requirementType,
                null,
                null,
                requirementDigest,
                requirementName,
                requirementSummary,
                department,
                businessLine,
                remark,
                plannedDueDate,
                developmentStartedDate,
                actualEffort,
                testingStartedDate,
                actualCompletedTime,
                acceptanceTime,
                null,
                null,
                null,
                projectHint,
                fields,
                List.of(),
                null
        );
    }

    private List<IntakeStructuredField> parseFields(String rawContent) {
        List<IntakeStructuredField> fields = new ArrayList<>();
        IntakeStructuredField lastField = null;
        for (String line : rawContent.split("\n")) {
            String trimmed = trimToNull(line);
            if (trimmed == null) {
                continue;
            }
            int separatorIndex = findSeparatorIndex(trimmed);
            if (separatorIndex > 0) {
                String label = trimToNull(trimmed.substring(0, separatorIndex));
                String value = trimToNull(trimmed.substring(separatorIndex + 1));
                if (label != null) {
                    lastField = new IntakeStructuredField(label, value == null ? "" : value);
                    fields.add(lastField);
                    continue;
                }
            }
            if (lastField != null) {
                IntakeStructuredField merged = new IntakeStructuredField(
                        lastField.label(),
                        appendFieldValue(lastField.value(), trimmed)
                );
                fields.set(fields.size() - 1, merged);
                lastField = merged;
            }
        }
        return fields;
    }

    private int findSeparatorIndex(String value) {
        int fullWidth = value.indexOf('：');
        if (fullWidth > 0) {
            return fullWidth;
        }
        return value.indexOf(':');
    }

    private String firstValue(List<IntakeStructuredField> fields, String... labels) {
        for (String label : labels) {
            for (IntakeStructuredField field : fields) {
                if (label.equals(field.label())) {
                    return trimToNull(field.value());
                }
            }
        }
        return null;
    }

    private String firstNonBlank(String preferred, String fallback) {
        String preferredValue = trimToNull(preferred);
        return preferredValue != null ? preferredValue : trimToNull(fallback);
    }

    private String appendFieldValue(String currentValue, String nextLine) {
        String normalizedCurrent = currentValue == null ? "" : currentValue;
        if (normalizedCurrent.isBlank()) {
            return nextLine;
        }
        return normalizedCurrent + "\n" + nextLine;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String extractProposerName(String approvalTitle) {
        String normalized = trimToNull(approvalTitle);
        if (normalized == null) {
            return null;
        }
        int separatorIndex = normalized.indexOf('的');
        if (separatorIndex <= 0) {
            return null;
        }
        return trimToNull(normalized.substring(0, separatorIndex));
    }

    private String inferRequirementType(String rawContent) {
        String normalized = trimToNull(rawContent);
        if (normalized == null) {
            return "研发需求";
        }
        if (containsAny(normalized, "数据提取", "数据同步", "报表", "导出", "导入", "跑数", "取数", "查询数据")
                || containsAny(normalized, "运维", "重启", "巡检", "配置", "上线处理", "部署", "发布处理")) {
            return "数据提取/运维";
        }
        return "研发需求";
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String buildRequirementDigest(String requirementName, String requirementSummary, String approvalTitle) {
        String source = firstNonBlank(requirementSummary, firstNonBlank(requirementName, approvalTitle));
        if (source == null) {
            return null;
        }
        String normalized = source.replace("\n", " ").replace("\r", " ").trim().replaceAll("\\s+", "");
        if (normalized.length() <= 24) {
            return normalized;
        }
        return normalized.substring(0, 24);
    }
}
