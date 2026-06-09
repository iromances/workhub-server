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
 * 修复历史研发需求提测日期：旧逻辑把测试通过日期写入 testingStartedDate。
 */
@Component
@Order(3)
public class IntakeTestingDateMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IntakeTestingDateMigrationRunner.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final IntakeMapper intakeMapper;
    private final IntakeHistoryMapper intakeHistoryMapper;
    private final ObjectMapper objectMapper;

    public IntakeTestingDateMigrationRunner(IntakeMapper intakeMapper,
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
            if (!"研发需求".equals(structuredData.requirementType())) {
                continue;
            }
            IntakeHistoryEntity submitTestingHistory = intakeHistoryMapper.findLatestUpdateBySummary(entity.getId(), "提交测试");
            if (submitTestingHistory == null || submitTestingHistory.getCreatedAt() == null) {
                continue;
            }
            String submitTestingDate = submitTestingHistory.getCreatedAt().format(DATE_FORMATTER);
            String currentTestingStartedDate = trimToNull(structuredData.testingStartedDate());
            if (Objects.equals(submitTestingDate, currentTestingStartedDate)) {
                continue;
            }
            if (currentTestingStartedDate != null) {
                IntakeHistoryEntity passTestingHistory = intakeHistoryMapper.findLatestUpdateBySummary(entity.getId(), "测试通过");
                if (passTestingHistory == null || passTestingHistory.getCreatedAt() == null) {
                    continue;
                }
                String passTestingDate = passTestingHistory.getCreatedAt().format(DATE_FORMATTER);
                if (!Objects.equals(currentTestingStartedDate, passTestingDate)) {
                    continue;
                }
            }
            IntakeStructuredData fixed = withTestingStartedDate(structuredData, submitTestingDate);
            intakeMapper.updateStructuredData(entity.getId(), writeStructuredData(fixed));
            changed++;
        }
        if (changed > 0) {
            log.warn("Migrated actual testing dates for {} intake records", changed);
        }
    }

    private IntakeStructuredData withTestingStartedDate(IntakeStructuredData structuredData, String testingStartedDate) {
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
                structuredData.plannedTestingStartDate(),
                structuredData.plannedReleaseDate(),
                structuredData.developmentStartedDate(),
                structuredData.actualEffort(),
                testingStartedDate,
                structuredData.actualCompletedTime(),
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

    private IntakeStructuredData readStructuredData(String json) {
        try {
            return objectMapper.readValue(json, IntakeStructuredData.class);
        } catch (JacksonException ex) {
            throw new IllegalStateException("迁移历史提测日期时解析 structured_data_json 失败", ex);
        }
    }

    private String writeStructuredData(IntakeStructuredData structuredData) {
        try {
            return objectMapper.writeValueAsString(structuredData);
        } catch (JacksonException ex) {
            throw new IllegalStateException("迁移历史提测日期时写回 structured_data_json 失败", ex);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
