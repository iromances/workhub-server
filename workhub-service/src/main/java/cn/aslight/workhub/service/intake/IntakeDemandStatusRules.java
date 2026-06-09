package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import java.util.List;

/**
 * 需求管理阶段的生命周期状态规则。
 */
final class IntakeDemandStatusRules {

    static final String RECORDED = "已收录";
    static final String CLARIFYING = "待澄清";
    static final String PENDING_PROCESSING = "待处理";
    static final String PROCESSING = "处理中";
    static final String PENDING_EVALUATION = "待评估";
    static final String PENDING_SCHEDULING = "待排期";
    static final String PENDING_DESIGN = "待设计";
    static final String IN_DEVELOPMENT = "开发中";
    static final String TESTING = "测试中";
    static final String PENDING_RELEASE = "待上线";
    static final String PENDING_ACCEPTANCE = "待验收";
    static final String COMPLETED = "已完成";
    static final String TERMINATED = "终止关闭";
    static final String PAUSED = "已暂停";

    static final String ACTION_START_CLARIFICATION = "START_CLARIFICATION";
    static final String ACTION_CONFIRM_RECORDED = "CONFIRM_RECORDED";
    static final String ACTION_CONFIRM_CLARIFICATION = "CONFIRM_CLARIFICATION";
    static final String ACTION_START_PROCESSING = "START_PROCESSING";
    static final String ACTION_SUBMIT_ACCEPTANCE = "SUBMIT_ACCEPTANCE";
    static final String ACTION_COMPLETE_EVALUATION = "COMPLETE_EVALUATION";
    static final String ACTION_CONFIRM_SCHEDULING = "CONFIRM_SCHEDULING";
    static final String ACTION_CONFIRM_DESIGN = "CONFIRM_DESIGN";
    static final String ACTION_SUBMIT_TESTING = "SUBMIT_TESTING";
    static final String ACTION_PASS_TESTING = "PASS_TESTING";
    static final String ACTION_CONFIRM_RELEASE = "CONFIRM_RELEASE";
    static final String ACTION_CONFIRM_ACCEPTANCE = "CONFIRM_ACCEPTANCE";
    static final String ACTION_CLOSE_REQUIREMENT = "CLOSE_REQUIREMENT";

    static final List<String> EDITABLE_BUSINESS_STATUSES = List.of(
            RECORDED,
            CLARIFYING,
            PENDING_PROCESSING,
            PROCESSING,
            PENDING_EVALUATION,
            PENDING_SCHEDULING,
            PENDING_DESIGN,
            IN_DEVELOPMENT,
            TESTING,
            PENDING_RELEASE,
            PENDING_ACCEPTANCE,
            PAUSED,
            COMPLETED,
            TERMINATED
    );

    private IntakeDemandStatusRules() {
    }

    static String resolve(IntakeRecordEntity entity, IntakeStructuredData structuredData) {
        if (entity == null) {
            return null;
        }
        String enrichmentStatus = resolveEnrichmentStatus(entity);
        String storedStatus = normalizeLegacyStatus(entity.getDemandStatus());
        if (IntakeEnrichmentStatus.FAILED.equals(enrichmentStatus)) {
            return storedStatus == null || RECORDED.equals(storedStatus) ? null : storedStatus;
        }
        if (IntakeEnrichmentStatus.PENDING.equals(enrichmentStatus) || IntakeEnrichmentStatus.RUNNING.equals(enrichmentStatus)) {
            return storedStatus;
        }
        if (storedStatus != null) {
            return storedStatus;
        }
        return hasMeaningfulStructuredData(structuredData) ? RECORDED : null;
    }

    static String resolveWhenRunning(String currentStatus) {
        return normalizeLegacyStatus(currentStatus);
    }

    static String resolveAfterEnrichment(String currentStatus,
                                         String enrichmentStatus,
                                         IntakeStructuredData structuredData) {
        String normalized = normalizeLegacyStatus(currentStatus);
        if (normalized != null) {
            return normalized;
        }
        if (IntakeEnrichmentStatus.SUCCEEDED.equals(enrichmentStatus) && hasMeaningfulStructuredData(structuredData)) {
            return RECORDED;
        }
        return null;
    }

    static String normalizeEditableStatus(String demandStatus) {
        String normalized = normalizeLegacyStatus(demandStatus);
        if (normalized == null) {
            return null;
        }
        if (EDITABLE_BUSINESS_STATUSES.contains(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("当前仅允许维护业务阶段状态: 已收录 / 待澄清 / 待处理 / 处理中 / 待评估 / 待排期 / 待设计 / 开发中 / 测试中 / 待验收 / 待上线 / 已暂停 / 已完成 / 终止关闭");
    }

    static String resolveNextStatus(String currentStatus, String requirementType, String action) {
        String normalizedStatus = normalizeEditableStatus(currentStatus);
        String normalizedAction = normalizeAction(action);
        if (normalizedStatus == null) {
            throw new IllegalArgumentException("需求尚未进入业务生命周期");
        }
        if (isTerminalStatus(normalizedStatus)) {
            throw new IllegalArgumentException("终态需求不允许继续推进");
        }
        if (PAUSED.equals(normalizedStatus)) {
            throw new IllegalArgumentException("暂停需求请先恢复后再推进");
        }
        if (isOperationsRequirement(requirementType)) {
            return resolveOperationsNextStatus(normalizedStatus, normalizedAction);
        }
        return switch (normalizedAction) {
            case ACTION_START_CLARIFICATION -> requireTransition(normalizedStatus, RECORDED, CLARIFYING, "仅已收录需求允许开始澄清");
            case ACTION_CONFIRM_CLARIFICATION -> requireTransition(normalizedStatus, CLARIFYING, PENDING_EVALUATION, "仅待澄清需求允许确认澄清完成");
            case ACTION_COMPLETE_EVALUATION -> throw new IllegalArgumentException("待评估需求必须通过研发任务评估确认推进到待排期");
            case ACTION_CONFIRM_SCHEDULING -> requireTransition(normalizedStatus, PENDING_SCHEDULING, PENDING_DESIGN, "仅待排期需求允许确认排期");
            case ACTION_CONFIRM_DESIGN -> requireTransition(normalizedStatus, PENDING_DESIGN, IN_DEVELOPMENT, "仅待设计需求允许确认设计完成");
            case ACTION_SUBMIT_TESTING -> requireTransition(normalizedStatus, IN_DEVELOPMENT, TESTING, "仅开发中需求允许提交测试");
            case ACTION_PASS_TESTING -> requireTransition(normalizedStatus, TESTING, PENDING_ACCEPTANCE, "仅测试中需求允许确认测试通过");
            case ACTION_CONFIRM_ACCEPTANCE -> requireTransition(normalizedStatus, PENDING_ACCEPTANCE, PENDING_RELEASE, "仅待验收需求允许确认验收通过");
            case ACTION_CONFIRM_RELEASE -> requireTransition(normalizedStatus, PENDING_RELEASE, COMPLETED, "仅待上线需求允许确认上线");
            case ACTION_CLOSE_REQUIREMENT -> TERMINATED;
            default -> throw new IllegalArgumentException("不支持的阶段动作");
        };
    }

    private static String resolveOperationsNextStatus(String normalizedStatus, String normalizedAction) {
        return switch (normalizedAction) {
            case ACTION_START_CLARIFICATION -> requireTransition(normalizedStatus, RECORDED, CLARIFYING, "仅已收录需求允许开始澄清");
            case ACTION_CONFIRM_RECORDED -> requireTransition(normalizedStatus, RECORDED, PENDING_PROCESSING, "仅已收录数据运维需求允许确认收录");
            case ACTION_CONFIRM_CLARIFICATION -> requireTransition(normalizedStatus, CLARIFYING, PENDING_PROCESSING, "仅待澄清数据运维需求允许确认澄清完成");
            case ACTION_START_PROCESSING -> requireTransition(normalizedStatus, PENDING_PROCESSING, PROCESSING, "仅待处理数据运维需求允许开始处理");
            case ACTION_SUBMIT_ACCEPTANCE -> requireTransition(normalizedStatus, PROCESSING, PENDING_ACCEPTANCE, "仅处理中数据运维需求允许提交验收");
            case ACTION_CONFIRM_ACCEPTANCE -> requireTransition(normalizedStatus, PENDING_ACCEPTANCE, COMPLETED, "仅待验收数据运维需求允许确认验收通过");
            case ACTION_CLOSE_REQUIREMENT -> TERMINATED;
            default -> throw new IllegalArgumentException("数据运维需求不支持该阶段动作: " + normalizedAction);
        };
    }

    static String normalizeAction(String action) {
        String normalized = trimToNull(action);
        if (normalized == null) {
            throw new IllegalArgumentException("阶段动作不能为空");
        }
        return switch (normalized) {
            case ACTION_START_CLARIFICATION,
                    ACTION_CONFIRM_RECORDED,
                    ACTION_CONFIRM_CLARIFICATION,
                    ACTION_START_PROCESSING,
                    ACTION_SUBMIT_ACCEPTANCE,
                    ACTION_COMPLETE_EVALUATION,
                    ACTION_CONFIRM_SCHEDULING,
                    ACTION_CONFIRM_DESIGN,
                    ACTION_SUBMIT_TESTING,
                    ACTION_PASS_TESTING,
                    ACTION_CONFIRM_RELEASE,
                    ACTION_CONFIRM_ACCEPTANCE,
                    ACTION_CLOSE_REQUIREMENT -> normalized;
            default -> throw new IllegalArgumentException("不支持的阶段动作: " + normalized);
        };
    }

    private static boolean isOperationsRequirement(String requirementType) {
        return "数据提取/运维".equals(trimToNull(requirementType));
    }

    static boolean isTerminalStatus(String demandStatus) {
        return COMPLETED.equals(demandStatus) || TERMINATED.equals(demandStatus);
    }

    static String resolveEnrichmentStatus(IntakeRecordEntity entity) {
        if (entity == null) {
            return null;
        }
        String enrichmentStatus = trimToNull(entity.getEnrichmentStatus());
        if (IntakeEnrichmentStatus.SUCCEEDED.equals(enrichmentStatus) && hasCodexFailureSummary(entity.getEnrichmentErrorSummary())) {
            return IntakeEnrichmentStatus.FAILED;
        }
        return enrichmentStatus;
    }

    static String normalizeLegacyStatus(String demandStatus) {
        String normalized = trimToNull(demandStatus);
        if (normalized == null) {
            return null;
        }
        return switch (normalized) {
            case "待确认" -> RECORDED;
            case "待识别", "识别中", "识别失败" -> null;
            case "研发中" -> IN_DEVELOPMENT;
            case "待测试" -> TESTING;
            case "已上线" -> COMPLETED;
            case "已关闭" -> TERMINATED;
            case "评估中", "已评估，待确认" -> PENDING_EVALUATION;
            case "已评估" -> PENDING_SCHEDULING;
            default -> normalized;
        };
    }

    private static String requireTransition(String currentStatus, String expectedStatus, String nextStatus, String message) {
        if (!expectedStatus.equals(currentStatus)) {
            throw new IllegalArgumentException(message);
        }
        return nextStatus;
    }

    private static boolean hasCodexFailureSummary(String errorSummary) {
        String normalized = trimToNull(errorSummary);
        return normalized != null && normalized.contains("Codex CLI");
    }

    private static boolean hasMeaningfulStructuredData(IntakeStructuredData structuredData) {
        if (structuredData == null) {
            return false;
        }
        return firstNonBlank(
                structuredData.approvalTitle(),
                firstNonBlank(
                        structuredData.proposerName(),
                        firstNonBlank(
                                structuredData.approvalCode(),
                                firstNonBlank(
                                        structuredData.submittedTime(),
                                        firstNonBlank(
                                                structuredData.requirementType(),
                                                firstNonBlank(
                                                        structuredData.requirementDigest(),
                                                        firstNonBlank(
                                                                structuredData.requirementName(),
                                                                firstNonBlank(
                                                                        structuredData.requirementSummary(),
                                                                        firstNonBlank(
                                                                                        structuredData.department(),
                                                                                        firstNonBlank(
                                                                                                structuredData.businessLine(),
                                                                                                structuredData.remark()
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        ) != null;
    }

    private static String firstNonBlank(String preferred, String fallback) {
        String preferredValue = trimToNull(preferred);
        return preferredValue != null ? preferredValue : trimToNull(fallback);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
