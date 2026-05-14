package cn.aslight.workhub.config;

import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.model.intake.IntakeAIDraft;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.service.intake.EffortUnitNormalizer;
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

/**
 * 启动时统一修复历史工时单位为小时。
 */
@Component
@Order(2)
public class IntakeEffortNormalizationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IntakeEffortNormalizationRunner.class);

    private final IntakeMapper intakeMapper;
    private final ObjectMapper objectMapper;

    public IntakeEffortNormalizationRunner(IntakeMapper intakeMapper, ObjectMapper objectMapper) {
        this.intakeMapper = intakeMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<IntakeRecordEntity> records = intakeMapper.findAllForEffortNormalization();
        int updatedCount = 0;
        for (IntakeRecordEntity record : records) {
            String nextStructuredDataJson = normalizeStructuredDataJson(record.getStructuredDataJson());
            String nextAiDraftJson = normalizeAiDraftJson(record.getAiDraftJson());
            if (Objects.equals(nextStructuredDataJson, record.getStructuredDataJson())
                    && Objects.equals(nextAiDraftJson, record.getAiDraftJson())) {
                continue;
            }
            intakeMapper.updateEffortPayloads(record.getId(), nextStructuredDataJson, nextAiDraftJson);
            updatedCount++;
        }
        if (updatedCount > 0) {
            log.warn("Normalized effort units to hours for {} intake records", updatedCount);
        }
    }

    private String normalizeStructuredDataJson(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        try {
            IntakeStructuredData structuredData = objectMapper.readValue(json, IntakeStructuredData.class);
            IntakeStructuredData normalized = EffortUnitNormalizer.normalizeStructuredData(structuredData);
            if (Objects.equals(structuredData, normalized)) {
                return json;
            }
            return objectMapper.writeValueAsString(normalized);
        } catch (JacksonException ex) {
            throw new IllegalStateException("历史 structured_data_json 工时修复失败", ex);
        }
    }

    private String normalizeAiDraftJson(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        try {
            IntakeAIDraft draft = objectMapper.readValue(json, IntakeAIDraft.class);
            IntakeAIDraft normalized = EffortUnitNormalizer.normalizeDraft(draft);
            if (Objects.equals(draft, normalized)) {
                return json;
            }
            return objectMapper.writeValueAsString(normalized);
        } catch (JacksonException ex) {
            throw new IllegalStateException("历史 ai_draft_json 工时修复失败", ex);
        }
    }
}
