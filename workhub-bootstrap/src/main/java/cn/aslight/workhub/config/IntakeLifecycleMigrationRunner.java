package cn.aslight.workhub.config;

import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Objects;

@Component
@Order(1)
public class IntakeLifecycleMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IntakeLifecycleMigrationRunner.class);

    private final IntakeMapper intakeMapper;
    private final ObjectMapper objectMapper;

    public IntakeLifecycleMigrationRunner(IntakeMapper intakeMapper, ObjectMapper objectMapper) {
        this.intakeMapper = intakeMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        int changed = 0;
        for (IntakeRecordEntity entity : intakeMapper.findAllForLifecycleMigration()) {
            String normalizedStatus = normalizeLegacyStatus(entity.getDemandStatus());
            String rawJson = trimToNull(entity.getStructuredDataJson());
            String developmentOwnerUserName = trimToNull(entity.getDevelopmentOwnerUserName());
            String normalizedJson = rawJson;
            if (rawJson != null) {
                IntakeStructuredData structuredData = readStructuredData(rawJson);
                if (developmentOwnerUserName == null) {
                    developmentOwnerUserName = trimToNull(structuredData.developmentOwnerUserName());
                }
                IntakeStructuredData cleaned = stripDevelopmentOwner(structuredData);
                normalizedJson = writeStructuredData(cleaned);
            }
            if (Objects.equals(normalizedStatus, entity.getDemandStatus())
                    && Objects.equals(developmentOwnerUserName, trimToNull(entity.getDevelopmentOwnerUserName()))
                    && Objects.equals(normalizedJson, rawJson)) {
                continue;
            }
            intakeMapper.updateLifecycleFields(entity.getId(), normalizedJson, developmentOwnerUserName, normalizedStatus);
            changed++;
        }
        if (changed > 0) {
            log.warn("Migrated intake lifecycle fields for {} records", changed);
        }
    }

    private IntakeStructuredData readStructuredData(String json) {
        try {
            return objectMapper.readValue(json, IntakeStructuredData.class);
        } catch (JacksonException ex) {
            throw new IllegalStateException("迁移需求生命周期时解析 structured_data_json 失败", ex);
        }
    }

    private String writeStructuredData(IntakeStructuredData structuredData) {
        try {
            return objectMapper.writeValueAsString(structuredData);
        } catch (JacksonException ex) {
            throw new IllegalStateException("迁移需求生命周期时写回 structured_data_json 失败", ex);
        }
    }

    private IntakeStructuredData stripDevelopmentOwner(IntakeStructuredData structuredData) {
        return new IntakeStructuredData(
                structuredData.category(),
                structuredData.approvalTitle(),
                structuredData.proposerName(),
                null,
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
                structuredData.plannedTestingStartDate(),
                structuredData.plannedReleaseDate(),
                structuredData.developmentStartedDate(),
                structuredData.actualEffort(),
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeLegacyStatus(String demandStatus) {
        String normalized = trimToNull(demandStatus);
        if (normalized == null) {
            return null;
        }
        return switch (normalized) {
            case "待确认" -> "已收录";
            case "待识别", "识别中", "识别失败" -> null;
            case "研发中" -> "开发中";
            case "待测试" -> "测试中";
            case "已上线" -> "已完成";
            case "已关闭" -> "终止关闭";
            case "评估中", "已评估，待确认" -> "待评估";
            case "已评估" -> "待排期";
            default -> normalized;
        };
    }
}
