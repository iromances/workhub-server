package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.intake.IntakeStructuredField;
import cn.aslight.workhub.model.intake.IntakeStructuredFieldEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntakeStructuredFieldNormalizerTest {

    @Test
    void toFieldEntities_shouldLimitLongLabelAndPreserveOriginalTextInValue() {
        String originalLabel = "字段".repeat(70);
        IntakeStructuredData structuredData = structuredDataWithFields(List.of(
                new IntakeStructuredField(originalLabel, "原始值")
        ));

        List<IntakeStructuredFieldEntity> entities = new IntakeStructuredFieldNormalizer()
                .toFieldEntities(143L, structuredData);

        assertEquals(1, entities.size());
        IntakeStructuredFieldEntity entity = entities.getFirst();
        assertEquals(IntakeStructuredFieldNormalizer.MAX_FIELD_LABEL_LENGTH,
                entity.getFieldLabel().codePointCount(0, entity.getFieldLabel().length()));
        assertTrue(entity.getFieldLabel().endsWith("…"));
        assertTrue(entity.getFieldValue().contains(originalLabel));
        assertTrue(entity.getFieldValue().endsWith("原始值"));
    }

    @Test
    void toFieldEntities_shouldKeepNormalLabelAndValueUnchanged() {
        IntakeStructuredData structuredData = structuredDataWithFields(List.of(
                new IntakeStructuredField("需求名称", "线上扣款需求")
        ));

        IntakeStructuredFieldEntity entity = new IntakeStructuredFieldNormalizer()
                .toFieldEntities(141L, structuredData)
                .getFirst();

        assertEquals("需求名称", entity.getFieldLabel());
        assertEquals("线上扣款需求", entity.getFieldValue());
        assertEquals("requirement_name", entity.getNormalizedKey());
    }

    private IntakeStructuredData structuredDataWithFields(List<IntakeStructuredField> fields) {
        return new IntakeStructuredData(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                fields,
                List.of(),
                null
        );
    }
}
