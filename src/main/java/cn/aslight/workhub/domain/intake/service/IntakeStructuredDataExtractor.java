package cn.aslight.workhub.domain.intake.service;

import cn.aslight.workhub.domain.intake.dto.IntakeStructuredData;
import cn.aslight.workhub.domain.intake.dto.IntakeAttachmentSummary;
import cn.aslight.workhub.domain.intake.dto.IntakeStructuredField;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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
        String approvalCode = firstValue(fields, "审批编号", "单号", "审批单号");
        String requirementName = firstValue(fields, "需求名称", "需求标题");
        String requirementSummary = firstValue(fields, "需求简介", "需求描述", "需求说明");
        String department = firstValue(fields, "所在部门", "申请部门");
        String businessLine = firstValue(fields, "需求所属业务线", "业务线");
        String remark = firstValue(fields, "备注");
        String estimatedEffort = firstValue(fields, "预估工时", "工时");
        String plannedDueDate = firstValue(fields, "预估完成时间", "计划完成时间", "预计完成时间");
        String projectHint = firstNonBlank(businessLine, department);
        String category = requirementName != null || requirementSummary != null ? "需求审批" : "待整理项";

        return new IntakeStructuredData(
                category,
                approvalTitle,
                approvalCode,
                requirementName,
                requirementSummary,
                department,
                businessLine,
                remark,
                estimatedEffort,
                plannedDueDate,
                projectHint,
                fields,
                List.of()
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
}
