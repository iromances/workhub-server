package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import java.util.List;

/**
 * 需求管理阶段的生命周期状态规则。
 */
final class IntakeDemandStatusRules {

    static final String RECORDED = "已收录";
    static final String PENDING_EVALUATION = "待评估";
    static final String EVALUATED = "已评估";
    static final String IN_DEVELOPMENT = "研发中";
    static final String PENDING_TEST = "待测试";
    static final String TESTING = "测试中";
    static final String PENDING_ACCEPTANCE = "待验收";
    static final String PENDING_RELEASE = "待上线";
    static final String RELEASED = "已上线";
    static final String ACTION_EVALUATE_EFFORT = "EVALUATE_EFFORT";
    static final String ACTION_START_DEVELOPMENT = "START_DEVELOPMENT";
    static final String ACTION_SUBMIT_TESTING = "SUBMIT_TESTING";
    static final String ACTION_START_TESTING = "START_TESTING";
    static final String ACTION_SUBMIT_ACCEPTANCE = "SUBMIT_ACCEPTANCE";
    static final String ACTION_CONFIRM_ACCEPTANCE = "CONFIRM_ACCEPTANCE";
    static final String ACTION_CONFIRM_RELEASE = "CONFIRM_RELEASE";
    static final List<String> EDITABLE_BUSINESS_STATUSES = List.of(
            RECORDED,
            PENDING_EVALUATION,
            EVALUATED,
            IN_DEVELOPMENT,
            PENDING_TEST,
            TESTING,
            PENDING_ACCEPTANCE,
            PENDING_RELEASE,
            RELEASED
    );

    private IntakeDemandStatusRules() {
    }

    /**
     * 根据 intake 当前记录状态解析需求管理侧状态。
     *
     * @param entity          待整理记录
     * @param structuredData  当前可读到的结构化数据
     * @return 需求管理侧状态
     */
    static String resolve(IntakeRecordEntity entity, IntakeStructuredData structuredData) {
        if (entity == null) {
            return null;
        }
        String storedStatus = normalizeLegacyStatus(entity.getDemandStatus());
        if (storedStatus != null) {
            return storedStatus;
        }
        return hasMeaningfulStructuredData(structuredData) ? RECORDED : null;
    }

    /**
     * 在异步增强运行时推导需求状态。
     *
     * @param currentStatus 当前需求状态
     * @return 推导后的需求状态
     */
    static String resolveWhenRunning(String currentStatus) {
        return normalizeLegacyStatus(currentStatus);
    }

    /**
     * 在异步增强完成后推导需求状态。
     *
     * @param currentStatus 当前需求状态
     * @param enrichmentStatus 本次增强结果
     * @param structuredData 本次可用结构化结果
     * @return 推导后的需求状态
     */
    static String resolveAfterEnrichment(String currentStatus,
                                         String enrichmentStatus,
                                         IntakeStructuredData structuredData) {
        String normalized = trimToNull(currentStatus);
        if (normalized != null) {
            return normalizeLegacyStatus(normalized);
        }
        if (IntakeEnrichmentStatus.SUCCEEDED.equals(enrichmentStatus) && hasMeaningfulStructuredData(structuredData)) {
            return RECORDED;
        }
        return null;
    }

    /**
     * 校验并标准化可编辑的需求状态。
     *
     * @param demandStatus 输入状态
     * @return 标准化后的状态
     */
    static String normalizeEditableStatus(String demandStatus) {
        String normalized = normalizeLegacyStatus(demandStatus);
        if (normalized == null) {
            return null;
        }
        if (EDITABLE_BUSINESS_STATUSES.contains(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("当前仅允许维护业务阶段状态: 已收录 / 待评估 / 已评估 / 研发中 / 待测试 / 测试中 / 待验收 / 待上线 / 已上线");
    }

    /**
     * 判断当前需求状态是否已进入人工管理阶段。
     *
     * @param demandStatus 当前需求状态
     * @return 是否允许人工编辑业务字段
     */
    static boolean isEditableBusinessStatus(String demandStatus) {
        String normalized = normalizeLegacyStatus(demandStatus);
        return normalized != null && EDITABLE_BUSINESS_STATUSES.contains(normalized);
    }

    /**
     * 根据动作解析下一阶段状态。
     *
     * @param currentStatus 当前需求状态
     * @param action 阶段动作
     * @return 下一阶段状态
     */
    static String resolveNextStatus(String currentStatus, String action) {
        String normalizedStatus = normalizeEditableStatus(currentStatus);
        String normalizedAction = normalizeAction(action);
        return switch (normalizedAction) {
            case ACTION_EVALUATE_EFFORT -> {
                if (!List.of(RECORDED, PENDING_EVALUATION).contains(normalizedStatus)) {
                    throw new IllegalArgumentException("当前阶段不允许执行评估工时");
                }
                yield EVALUATED;
            }
            case ACTION_START_DEVELOPMENT -> {
                if (!EVALUATED.equals(normalizedStatus)) {
                    throw new IllegalArgumentException("仅已评估需求允许开始研发");
                }
                yield IN_DEVELOPMENT;
            }
            case ACTION_SUBMIT_TESTING -> {
                if (!IN_DEVELOPMENT.equals(normalizedStatus)) {
                    throw new IllegalArgumentException("仅研发中需求允许提交测试");
                }
                yield PENDING_TEST;
            }
            case ACTION_START_TESTING -> {
                if (!PENDING_TEST.equals(normalizedStatus)) {
                    throw new IllegalArgumentException("仅待测试需求允许开始测试");
                }
                yield TESTING;
            }
            case ACTION_SUBMIT_ACCEPTANCE -> {
                if (!TESTING.equals(normalizedStatus)) {
                    throw new IllegalArgumentException("仅测试中需求允许提交验收");
                }
                yield PENDING_ACCEPTANCE;
            }
            case ACTION_CONFIRM_ACCEPTANCE -> {
                if (!PENDING_ACCEPTANCE.equals(normalizedStatus)) {
                    throw new IllegalArgumentException("仅待验收需求允许确认验收");
                }
                yield PENDING_RELEASE;
            }
            case ACTION_CONFIRM_RELEASE -> {
                if (!PENDING_RELEASE.equals(normalizedStatus)) {
                    throw new IllegalArgumentException("仅待上线需求允许确认上线");
                }
                yield RELEASED;
            }
            default -> throw new IllegalArgumentException("不支持的阶段动作");
        };
    }

    /**
     * 标准化阶段动作编码。
     *
     * @param action 原始动作编码
     * @return 标准化后的动作编码
     */
    static String normalizeAction(String action) {
        String normalized = trimToNull(action);
        if (normalized == null) {
            throw new IllegalArgumentException("阶段动作不能为空");
        }
        return switch (normalized) {
            case ACTION_EVALUATE_EFFORT,
                    ACTION_START_DEVELOPMENT,
                    ACTION_SUBMIT_TESTING,
                    ACTION_START_TESTING,
                    ACTION_SUBMIT_ACCEPTANCE,
                    ACTION_CONFIRM_ACCEPTANCE,
                    ACTION_CONFIRM_RELEASE -> normalized;
            default -> throw new IllegalArgumentException("不支持的阶段动作: " + normalized);
        };
    }

    private static String normalizeLegacyStatus(String demandStatus) {
        String normalized = trimToNull(demandStatus);
        if (normalized == null) {
            return null;
        }
        return switch (normalized) {
            case "待确认" -> RECORDED;
            case "待识别", "识别中", "识别失败" -> null;
            default -> normalized;
        };
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
                                                                        firstNonBlank(structuredData.remark(), structuredData.estimatedEffort())
                                                                )
                                                        )
                                                )
                                                ))
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
