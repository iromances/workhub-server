package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.model.intake.IntakeAIDraft;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.intake.IntakeTaskBreakdownItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EffortUnitNormalizerTest {

    @Test
    void normalizeEffort_shouldConvertDaysIntoHours() {
        assertEquals("16h", EffortUnitNormalizer.normalizeEffort("2d"));
        assertEquals("12h", EffortUnitNormalizer.normalizeEffort("1.5d"));
        assertEquals("8h", EffortUnitNormalizer.normalizeEffort("1 天"));
        assertEquals("24h", EffortUnitNormalizer.normalizeEffort("3day"));
        assertEquals("16h", EffortUnitNormalizer.normalizeEffort("16h"));
        assertEquals("8h", EffortUnitNormalizer.normalizeEffort("8"));
    }

    @Test
    void normalizeStructuredDataAndDraft_shouldNormalizeAllEffortFields() {
        IntakeStructuredData structuredData = new IntakeStructuredData(
                "需求审批",
                null,
                null,
                null,
                null,
                null,
                "研发需求",
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
                "1.5d",
                null,
                null,
                "2026/07/08",
                "0.5d",
                "2026/07/09",
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                null
        );
        IntakeAIDraft draft = new IntakeAIDraft(
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(
                        new IntakeTaskBreakdownItem("澄清", "0.5d", null, null, null),
                        new IntakeTaskBreakdownItem("开发", "8h", null, null, null)
                ),
                "heuristic",
                null,
                null
        );

        IntakeStructuredData normalizedStructuredData = EffortUnitNormalizer.normalizeStructuredData(structuredData);
        IntakeAIDraft normalizedDraft = EffortUnitNormalizer.normalizeDraft(draft);

        assertEquals("12h", normalizedStructuredData.actualEffort());
        assertEquals("4h", normalizedStructuredData.actualTestingEffort());
        assertEquals("2026/07/08", normalizedStructuredData.scheduledAcceptanceDate());
        assertEquals("2026/07/09", normalizedStructuredData.actualTestingCompletedDate());
        assertEquals("4h", normalizedDraft.taskBreakdownSuggestions().get(0).estimatedEffort());
        assertEquals("8h", normalizedDraft.taskBreakdownSuggestions().get(1).estimatedEffort());
    }
}
