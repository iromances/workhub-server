package cn.aslight.workhub.service.intake;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DevelopmentBranchNameGeneratorTest {

    @Test
    void normalize_shouldClearBranchNameForNonDevelopmentRequirement() {
        String branchName = DevelopmentBranchNameGenerator.normalize(
                "feature/requirement",
                "数据提取/运维",
                "202604240005",
                "2026/4/24 18:44",
                "【保费分期】退保数据修正",
                "修正退保批单金额错误",
                "退保批单金额错误，需要修正退保数据"
        );

        assertNull(branchName);
    }

    @Test
    void normalize_shouldGenerateShortBranchNameFromRequirementText() {
        String branchName = DevelopmentBranchNameGenerator.normalize(
                null,
                "研发需求",
                "202603240005",
                "2026/3/24 11:54",
                "里易二轮车换电项目迭代优化（三期）——新增分账核验规则",
                "新增分账核验规则并改造接口",
                null
        );

        assertEquals("feature/liyi_ebike_split_202603240005", branchName);
    }

    @Test
    void normalize_shouldReplaceExistingDateSuffixWithApprovalCode() {
        String branchName = DevelopmentBranchNameGenerator.normalize(
                "feature/very_long_requirement_branch_name_with_many_words_20260325",
                "研发需求",
                "202603250009",
                "2026/3/25 16:17",
                null,
                null,
                null
        );

        assertEquals("feature/very_long_requirement_branch_name_with_many_words_202603250009", branchName);
    }
}
