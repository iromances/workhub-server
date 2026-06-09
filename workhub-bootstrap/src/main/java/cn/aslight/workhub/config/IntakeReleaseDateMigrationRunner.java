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
import java.util.Set;

/**
 * 修复历史需求上线日期：releasedTime 只应来自“确认上线”阶段动作。
 */
@Component
@Order(4)
public class IntakeReleaseDateMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IntakeReleaseDateMigrationRunner.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final Set<String> AFTER_RELEASE_STATUSES = Set.of("已完成", "终止关闭");

    private final IntakeMapper intakeMapper;
    private final IntakeHistoryMapper intakeHistoryMapper;
    private final ObjectMapper objectMapper;

    public IntakeReleaseDateMigrationRunner(IntakeMapper intakeMapper,
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
            if ("数据提取/运维".equals(trimToNull(structuredData.requirementType()))
                    && trimToNull(structuredData.actualCompletedTime()) != null
                    && !Objects.equals(trimToNull(structuredData.actualCompletedTime()), trimToNull(structuredData.releasedTime()))) {
                IntakeStructuredData fixed = withReleasedTime(structuredData, trimToNull(structuredData.actualCompletedTime()));
                intakeMapper.updateStructuredData(entity.getId(), writeStructuredData(fixed));
                changed++;
                continue;
            }
            IntakeHistoryEntity releaseHistory = intakeHistoryMapper.findLatestUpdateBySummary(entity.getId(), "确认上线");
            String fixedReleaseDate = releaseHistory == null || releaseHistory.getCreatedAt() == null
                    ? null
                    : releaseHistory.getCreatedAt().format(DATE_FORMATTER);
            if (fixedReleaseDate == null
                    && (trimToNull(structuredData.releasedTime()) == null
                    || AFTER_RELEASE_STATUSES.contains(trimToNull(entity.getDemandStatus())))) {
                continue;
            }
            if (Objects.equals(fixedReleaseDate, trimToNull(structuredData.releasedTime()))) {
                continue;
            }
            IntakeStructuredData fixed = withReleasedTime(structuredData, fixedReleaseDate);
            intakeMapper.updateStructuredData(entity.getId(), writeStructuredData(fixed));
            changed++;
        }
        if (changed > 0) {
            log.warn("Migrated release dates for {} intake records", changed);
        }
    }

    private IntakeStructuredData withReleasedTime(IntakeStructuredData structuredData, String releasedTime) {
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
                structuredData.testingStartedDate(),
                structuredData.actualCompletedTime(),
                structuredData.acceptanceTime(),
                releasedTime,
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
            throw new IllegalStateException("迁移历史上线日期时解析 structured_data_json 失败", ex);
        }
    }

    private String writeStructuredData(IntakeStructuredData structuredData) {
        try {
            return objectMapper.writeValueAsString(structuredData);
        } catch (JacksonException ex) {
            throw new IllegalStateException("迁移历史上线日期时写回 structured_data_json 失败", ex);
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
