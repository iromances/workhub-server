package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.model.intake.IntakeAIDraft;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.intake.IntakeTaskBreakdownItem;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工时单位统一为小时（h）。
 */
public final class EffortUnitNormalizer {

    private static final Pattern EFFORT_PATTERN = Pattern.compile(
            "^([0-9]+(?:\\.[0-9]+)?)\\s*(d|day|days|天|人天|工作日|h|hour|hours|小时)?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final BigDecimal HOURS_PER_DAY = BigDecimal.valueOf(8);

    private EffortUnitNormalizer() {
    }

    public static String normalizeEffort(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        Matcher matcher = EFFORT_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return normalized;
        }
        BigDecimal amount = new BigDecimal(matcher.group(1));
        String unit = matcher.group(2) == null ? "h" : matcher.group(2).toLowerCase(Locale.ROOT);
        BigDecimal hours = isDayUnit(unit) ? amount.multiply(HOURS_PER_DAY) : amount;
        return formatHours(hours);
    }

    public static IntakeStructuredData normalizeStructuredData(IntakeStructuredData structuredData) {
        if (structuredData == null) {
            return null;
        }
        String actualEffort = normalizeEffort(structuredData.actualEffort());
        if (Objects.equals(actualEffort, structuredData.actualEffort())) {
            return structuredData;
        }
        return new IntakeStructuredData(
                structuredData.category(),
                structuredData.approvalTitle(),
                structuredData.proposerName(),
                structuredData.developmentOwnerUserName(),
                structuredData.approvalCode(),
                structuredData.submittedTime(),
                structuredData.requirementType(),
                structuredData.developmentBranchName(),
                structuredData.zentaoUrl(),
                structuredData.requirementDigest(),
                structuredData.requirementName(),
                structuredData.requirementSummary(),
                structuredData.department(),
                structuredData.businessLine(),
                structuredData.remark(),
                structuredData.plannedDueDate(),
                structuredData.plannedDevelopmentStartDate(),
                structuredData.plannedTestingStartDate(),
                structuredData.plannedReleaseDate(),
                structuredData.developmentStartedDate(),
                actualEffort,
                structuredData.testingStartedDate(),
                structuredData.actualCompletedTime(),
                structuredData.acceptanceTime(),
                structuredData.releasedTime(),
                structuredData.closedTime(),
                structuredData.closeReason(),
                structuredData.projectHint(),
                structuredData.fields(),
                structuredData.attachmentSummaries(),
                structuredData.sqlDraft()
        );
    }

    public static IntakeAIDraft normalizeDraft(IntakeAIDraft draft) {
        if (draft == null) {
            return null;
        }
        List<IntakeTaskBreakdownItem> tasks = draft.taskBreakdownSuggestions();
        if (tasks == null || tasks.isEmpty()) {
            return draft;
        }
        boolean changed = false;
        List<IntakeTaskBreakdownItem> normalizedTasks = new ArrayList<>(tasks.size());
        for (IntakeTaskBreakdownItem task : tasks) {
            if (task == null) {
                normalizedTasks.add(null);
                continue;
            }
            String normalizedEffort = normalizeEffort(task.estimatedEffort());
            if (!Objects.equals(normalizedEffort, task.estimatedEffort())) {
                changed = true;
            }
            normalizedTasks.add(new IntakeTaskBreakdownItem(
                    task.taskName(),
                    normalizedEffort,
                    task.ownerUserName(),
                    task.status(),
                    task.notes()
            ));
        }
        if (!changed) {
            return draft;
        }
        return new IntakeAIDraft(
                draft.titleSuggestion(),
                draft.descriptionSuggestion(),
                draft.typeSuggestion(),
                draft.prioritySuggestion(),
                draft.suggestedProjectCode(),
                draft.acceptanceCriteriaSuggestion(),
                normalizedTasks,
                draft.provider(),
                draft.model(),
                draft.rawResponse()
        );
    }

    private static boolean isDayUnit(String unit) {
        return "d".equals(unit)
                || "day".equals(unit)
                || "days".equals(unit)
                || "天".equals(unit)
                || "人天".equals(unit)
                || "工作日".equals(unit);
    }

    private static String formatHours(BigDecimal hours) {
        BigDecimal normalized = hours.stripTrailingZeros();
        return normalized.toPlainString() + "h";
    }
}
