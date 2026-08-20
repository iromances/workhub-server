package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.intake.IntakeStructuredField;
import cn.aslight.workhub.model.intake.IntakeStructuredFieldEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 需求结构化字段归一组件。
 */
@Component
public class IntakeStructuredFieldNormalizer {

    public static final String SOURCE_STRUCTURED_FIELD = "STRUCTURED_FIELD";
    public static final String SOURCE_ATTACHMENT_DOC_FIELD = "ATTACHMENT_DOC_FIELD";
    static final int MAX_FIELD_LABEL_LENGTH = 128;

    private static final Map<String, String> NORMALIZED_KEYS = Map.ofEntries(
            Map.entry("审批编号", "approval_code"),
            Map.entry("提交时间", "submitted_at"),
            Map.entry("提报时间", "submitted_at"),
            Map.entry("申请时间", "submitted_at"),
            Map.entry("提出时间", "submitted_at"),
            Map.entry("所在部门", "department"),
            Map.entry("申请部门", "department"),
            Map.entry("所属部门", "department"),
            Map.entry("审批标题", "approval_title"),
            Map.entry("标题", "approval_title"),
            Map.entry("审批状态", "approval_status"),
            Map.entry("状态", "approval_status"),
            Map.entry("提交人", "proposer_name"),
            Map.entry("申请人", "proposer_name"),
            Map.entry("提报人", "proposer_name"),
            Map.entry("提出人", "proposer_name"),
            Map.entry("发起人", "proposer_name"),
            Map.entry("提出方", "proposer_name"),
            Map.entry("需求类型", "requirement_type"),
            Map.entry("事项类型", "requirement_type"),
            Map.entry("requirementType", "requirement_type"),
            Map.entry("需求名称", "requirement_name"),
            Map.entry("需求标题", "requirement_name"),
            Map.entry("需求简介", "requirement_summary"),
            Map.entry("需求描述", "requirement_summary"),
            Map.entry("需求说明", "requirement_summary"),
            Map.entry("需求内容", "requirement_summary"),
            Map.entry("需求摘要", "requirement_digest"),
            Map.entry("摘要", "requirement_digest"),
            Map.entry("requirementDigest", "requirement_digest"),
            Map.entry("需求所属业务线", "business_line"),
            Map.entry("业务线", "business_line"),
            Map.entry("所属业务线", "business_line"),
            Map.entry("人工补充业务线", "business_line"),
            Map.entry("需求所属业务系统", "business_line"),
            Map.entry("项目组", "project_hint"),
            Map.entry("所属项目组", "project_hint"),
            Map.entry("人工补充项目组", "project_hint"),
            Map.entry("项目", "project_hint"),
            Map.entry("项目提示", "project_hint"),
            Map.entry("业务项目", "project_hint"),
            Map.entry("推荐研发分支名", "development_branch_name"),
            Map.entry("研发分支名", "development_branch_name"),
            Map.entry("developmentBranchName", "development_branch_name"),
            Map.entry("禅道地址", "zentao_url"),
            Map.entry("禅道链接", "zentao_url"),
            Map.entry("备注", "remark"),
            Map.entry("预估完成时间", "planned_due_date"),
            Map.entry("计划完成时间", "planned_due_date"),
            Map.entry("预计完成时间", "planned_due_date"),
            Map.entry("要求完成时间", "planned_due_date"),
            Map.entry("期望完成时间", "planned_due_date"),
            Map.entry("预估工时", "estimated_effort"),
            Map.entry("预计工时", "estimated_effort"),
            Map.entry("估计工时", "estimated_effort"),
            Map.entry("实际工时", "actual_effort"),
            Map.entry("实际完成时间", "actual_completed_date"),
            Map.entry("实际验收完成时间", "acceptance_date"),
            Map.entry("验收时间", "acceptance_date"),
            Map.entry("优先级", "priority"),
            Map.entry("紧急度", "urgency"),
            Map.entry("紧急程度", "urgency")
    );

    private static final List<String> ATTACHMENT_LABEL_KEYWORDS = List.of(
            "附件",
            "文档",
            "方案链接",
            "语雀文档链接",
            "原型地址",
            "接口文档",
            "截图"
    );

    private static final List<DateTimeFormatter> DATE_TIME_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy/M/d H:mm"),
            DateTimeFormatter.ofPattern("yyyy/M/d HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd H:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
    );

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy年M月d日")
    );

    public void applyStructuredData(IntakeRecordEntity entity, IntakeStructuredData structuredData) {
        if (entity == null || structuredData == null) {
            return;
        }
        entity.setApprovalCode(blankToNull(structuredData.approvalCode()));
        entity.setApprovalTitle(blankToNull(structuredData.approvalTitle()));
        entity.setProposerName(blankToNull(structuredData.proposerName()));
        entity.setSubmittedAt(parseDateTime(structuredData.submittedTime()));
        entity.setRequirementType(blankToNull(structuredData.requirementType()));
        entity.setRequirementName(blankToNull(structuredData.requirementName()));
        entity.setRequirementSummary(blankToNull(structuredData.requirementSummary()));
        entity.setRequirementDigest(blankToNull(structuredData.requirementDigest()));
        entity.setDepartment(blankToNull(structuredData.department()));
        entity.setBusinessLine(blankToNull(structuredData.businessLine()));
        entity.setBusinessLineCode(blankToNull(structuredData.businessLineCode()));
        entity.setProjectHint(blankToNull(structuredData.projectHint()));
        entity.setDevelopmentBranchName(blankToNull(structuredData.developmentBranchName()));
        entity.setZentaoUrl(blankToNull(structuredData.zentaoUrl()));
        entity.setRemark(blankToNull(structuredData.remark()));
        entity.setPlannedDueDate(parseDate(structuredData.plannedDueDate()));
        entity.setPlannedDevelopmentStartDate(parseDate(structuredData.plannedDevelopmentStartDate()));
        entity.setPlannedTestingStartDate(parseDate(structuredData.plannedTestingStartDate()));
        entity.setPlannedReleaseDate(parseDate(structuredData.plannedReleaseDate()));
        entity.setDevelopmentStartedDate(parseDate(structuredData.developmentStartedDate()));
        entity.setTestingStartedDate(parseDate(structuredData.testingStartedDate()));
        entity.setActualCompletedDate(parseDate(structuredData.actualCompletedTime()));
        entity.setScheduledAcceptanceDate(parseDate(structuredData.scheduledAcceptanceDate()));
        entity.setActualTestingCompletedDate(parseDate(structuredData.actualTestingCompletedDate()));
        entity.setAcceptanceDate(parseDate(structuredData.acceptanceTime()));
        entity.setReleasedDate(parseDate(structuredData.releasedTime()));
        entity.setClosedDate(parseDate(structuredData.closedTime()));
        entity.setCloseReason(blankToNull(structuredData.closeReason()));
        entity.setActualEffort(EffortUnitNormalizer.normalizeEffort(structuredData.actualEffort()));
        entity.setActualTestingEffort(EffortUnitNormalizer.normalizeEffort(structuredData.actualTestingEffort()));
        entity.setEstimatedEffort(extractEstimatedEffort(structuredData));
    }

    public List<IntakeStructuredFieldEntity> toFieldEntities(Long intakeId, IntakeStructuredData structuredData) {
        if (structuredData == null || structuredData.fields() == null || structuredData.fields().isEmpty()) {
            return List.of();
        }
        List<IntakeStructuredFieldEntity> entities = new ArrayList<>();
        int sortOrder = 0;
        for (IntakeStructuredField field : structuredData.fields()) {
            if (field == null || blankToNull(field.label()) == null) {
                continue;
            }
            String originalLabel = field.label().trim();
            String storedLabel = abbreviateLabel(originalLabel);
            String storedValue = blankToNull(field.value());
            if (!storedLabel.equals(originalLabel)) {
                storedValue = preserveOriginalLabel(originalLabel, storedValue);
            }
            IntakeStructuredFieldEntity entity = new IntakeStructuredFieldEntity();
            entity.setIntakeId(intakeId);
            entity.setFieldLabel(storedLabel);
            entity.setFieldValue(storedValue);
            entity.setNormalizedKey(normalizedKey(entity.getFieldLabel()));
            entity.setSourceType(resolveSourceType(entity.getFieldLabel()));
            entity.setSortOrder(sortOrder++);
            entities.add(entity);
        }
        return entities;
    }

    private String abbreviateLabel(String label) {
        int codePointCount = label.codePointCount(0, label.length());
        if (codePointCount <= MAX_FIELD_LABEL_LENGTH) {
            return label;
        }
        int endIndex = label.offsetByCodePoints(0, MAX_FIELD_LABEL_LENGTH - 1);
        return label.substring(0, endIndex) + "…";
    }

    private String preserveOriginalLabel(String originalLabel, String value) {
        String originalLabelLine = "原始字段名：" + originalLabel;
        return value == null ? originalLabelLine : originalLabelLine + "\n" + value;
    }

    public String normalizedKey(String label) {
        return NORMALIZED_KEYS.get(blankToNull(label));
    }

    private String resolveSourceType(String label) {
        for (String keyword : ATTACHMENT_LABEL_KEYWORDS) {
            if (label != null && label.contains(keyword)) {
                return SOURCE_ATTACHMENT_DOC_FIELD;
            }
        }
        return SOURCE_STRUCTURED_FIELD;
    }

    private String extractEstimatedEffort(IntakeStructuredData structuredData) {
        if (structuredData.fields() == null) {
            return null;
        }
        for (IntakeStructuredField field : structuredData.fields()) {
            String normalizedKey = normalizedKey(field == null ? null : field.label());
            if ("estimated_effort".equals(normalizedKey)) {
                return EffortUnitNormalizer.normalizeEffort(field.value());
            }
        }
        return null;
    }

    private LocalDateTime parseDateTime(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return null;
        }
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(normalized, formatter);
            } catch (DateTimeParseException ignored) {
                // try next formatter
            }
        }
        LocalDate date = parseDate(normalized);
        return date == null ? null : date.atStartOfDay();
    }

    private LocalDate parseDate(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return null;
        }
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(normalized, formatter);
            } catch (DateTimeParseException ignored) {
                // try next formatter
            }
        }
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(normalized, formatter).toLocalDate();
            } catch (DateTimeParseException ignored) {
                // try next formatter
            }
        }
        return null;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || "无".equals(normalized) || "null".equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized;
    }
}
