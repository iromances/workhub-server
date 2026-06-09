package cn.aslight.workhub.domain.intake.service;

import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.service.intake.IntakeStructuredDataExtractor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IntakeStructuredDataExtractorTest {

    @Test
    void extract_shouldNotPopulateReleasedTimeFromRawRequirementFields() {
        IntakeStructuredDataExtractor extractor = new IntakeStructuredDataExtractor();

        IntakeStructuredData structuredData = extractor.extract("""
                审批编号：REQ-REL-001
                需求类型：研发需求
                需求名称：上线日期识别测试
                上线时间：2026/04/10
                实际上线时间：2026/04/11
                """);

        assertEquals("REQ-REL-001", structuredData.approvalCode());
        assertNull(structuredData.actualCompletedTime());
        assertNull(structuredData.releasedTime());
    }
}
