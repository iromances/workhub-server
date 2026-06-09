package cn.aslight.workhub.config;

import cn.aslight.workhub.dao.intake.IntakeHistoryMapper;
import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.model.intake.IntakeHistoryEntity;
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

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * 回补历史数据运维需求的处理日期和提交验收日期。
 */
@Component
@Order(5)
public class IntakeOperationsDateMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IntakeOperationsDateMigrationRunner.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final IntakeMapper intakeMapper;
    private final IntakeHistoryMapper intakeHistoryMapper;
    private final ObjectMapper objectMapper;

    public IntakeOperationsDateMigrationRunner(IntakeMapper intakeMapper,
                                               IntakeHistoryMapper intakeHistoryMapper,
                                               ObjectMapper objectMapper) {
        this.intakeMapper = intakeMapper;
        this.intakeHistoryMapper = intakeHistoryMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        int changed = 0;
        for (IntakeRecordEntity entity : intakeMapper.findAllForLifecycleMigration()) {
            String rawJson = trimToNull(entity.getStructuredDataJson());
            if (rawJson == null) {
                continue;
            }
            IntakeStructuredData structuredData = readStructuredData(rawJson);
            if (!"数据提取/运维".equals(trimToNull(structuredData.requirementType()))) {
                continue;
            }
            String developmentStartedDate = firstNonBlank(
                    structuredData.developmentStartedDate(),
                    historyDate(entity.getId(), "开始处理")
            );
            String testingStartedDate = firstNonBlank(
                    structuredData.testingStartedDate(),
                    firstNonBlank(structuredData.actualCompletedTime(), historyDate(entity.getId(), "提交验收"))
            );
            if (Objects.equals(trimToNull(structuredData.developmentStartedDate()), developmentStartedDate)
                    && Objects.equals(trimToNull(structuredData.testingStartedDate()), testingStartedDate)) {
                continue;
            }
            IntakeStructuredData fixed = withOperationsDates(structuredData, developmentStartedDate, testingStartedDate);
            intakeMapper.updateStructuredData(entity.getId(), writeStructuredData(fixed));
            changed++;
        }
        if (changed > 0) {
            log.warn("Migrated operations dates for {} intake records", changed);
        }
    }

    private IntakeStructuredData withOperationsDates(IntakeStructuredData structuredData,
                                                     String developmentStartedDate,
                                                     String testingStartedDate) {
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
                developmentStartedDate,
                structuredData.actualEffort(),
                testingStartedDate,
                structuredData.actualCompletedTime(),
                structuredData.scheduledAcceptanceDate(),
                structuredData.actualTestingEffort(),
                structuredData.actualTestingCompletedDate(),
                structuredData.acceptanceTime(),
                structuredData.releasedTime(),
                structuredData.closedTime(),
                structuredData.closeReason(),
                structuredData.projectHint(),
                structuredData.fields() == null ? List.of() : structuredData.fields(),
                structuredData.attachmentSummaries() == null ? List.of() : structuredData.attachmentSummaries(),
                structuredData.sqlDraft()
        );
    }

    private String historyDate(Long intakeId, String actionSummary) {
        IntakeHistoryEntity history = intakeHistoryMapper.findLatestUpdateBySummary(intakeId, actionSummary);
        if (history == null || history.getCreatedAt() == null) {
            return null;
        }
        return history.getCreatedAt().format(DATE_FORMATTER);
    }

    private IntakeStructuredData readStructuredData(String json) {
        try {
            return objectMapper.readValue(json, IntakeStructuredData.class);
        } catch (JacksonException ex) {
            throw new IllegalStateException("迁移历史数据运维日期时解析 structured_data_json 失败", ex);
        }
    }

    private String writeStructuredData(IntakeStructuredData structuredData) {
        try {
            return objectMapper.writeValueAsString(structuredData);
        } catch (JacksonException ex) {
            throw new IllegalStateException("迁移历史数据运维日期时写回 structured_data_json 失败", ex);
        }
    }

    private String firstNonBlank(String preferred, String fallback) {
        String preferredValue = trimToNull(preferred);
        if (preferredValue != null) {
            return preferredValue;
        }
        return trimToNull(fallback);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
