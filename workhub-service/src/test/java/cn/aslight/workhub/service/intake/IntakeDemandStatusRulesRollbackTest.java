package cn.aslight.workhub.service.intake;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 阶段回退与"开发完成"状态的转移规则测试。
 */
class IntakeDemandStatusRulesRollbackTest {

    @Test
    void aiSelfTestPassedMovesInDevelopmentToDevelopmentCompleted() {
        assertEquals("开发完成", IntakeDemandStatusRules.resolveNextStatus(
                "开发中", "研发需求", "AI_SELF_TEST_PASSED"));
        assertThrows(IllegalArgumentException.class, () -> IntakeDemandStatusRules.resolveNextStatus(
                "待设计", "研发需求", "AI_SELF_TEST_PASSED"));
    }

    @Test
    void submitTestingAllowedFromBothInDevelopmentAndDevelopmentCompleted() {
        assertEquals("测试中", IntakeDemandStatusRules.resolveNextStatus(
                "开发中", "研发需求", "SUBMIT_TESTING"));
        assertEquals("测试中", IntakeDemandStatusRules.resolveNextStatus(
                "开发完成", "研发需求", "SUBMIT_TESTING"));
        assertThrows(IllegalArgumentException.class, () -> IntakeDemandStatusRules.resolveNextStatus(
                "待设计", "研发需求", "SUBMIT_TESTING"));
    }

    @Test
    void rollbackFollowsDevelopmentChain() {
        assertEquals("待设计", IntakeDemandStatusRules.resolveNextStatus(
                "开发中", "研发需求", "ROLLBACK_STAGE"));
        assertEquals("开发中", IntakeDemandStatusRules.resolveNextStatus(
                "开发完成", "研发需求", "ROLLBACK_STAGE"));
        assertEquals("开发完成", IntakeDemandStatusRules.resolveNextStatus(
                "测试中", "研发需求", "ROLLBACK_STAGE"));
        assertEquals("已收录", IntakeDemandStatusRules.resolveNextStatus(
                "待澄清", "研发需求", "ROLLBACK_STAGE"));
        assertEquals("待评估", IntakeDemandStatusRules.resolveNextStatus(
                "待排期", "研发需求", "ROLLBACK_STAGE"));
        assertEquals("待验收", IntakeDemandStatusRules.resolveNextStatus(
                "待上线", "研发需求", "ROLLBACK_STAGE"));
    }

    @Test
    void rollbackRejectedForFirstStageAndTerminalAndPaused() {
        assertThrows(IllegalArgumentException.class, () -> IntakeDemandStatusRules.resolveNextStatus(
                "已收录", "研发需求", "ROLLBACK_STAGE"));
        assertThrows(IllegalArgumentException.class, () -> IntakeDemandStatusRules.resolveNextStatus(
                "已完成", "研发需求", "ROLLBACK_STAGE"));
        assertThrows(IllegalArgumentException.class, () -> IntakeDemandStatusRules.resolveNextStatus(
                "已暂停", "研发需求", "ROLLBACK_STAGE"));
    }

    @Test
    void rollbackFollowsOperationsChain() {
        assertEquals("待处理", IntakeDemandStatusRules.resolveNextStatus(
                "处理中", "数据提取/运维", "ROLLBACK_STAGE"));
        assertEquals("待澄清", IntakeDemandStatusRules.resolveNextStatus(
                "待处理", "数据提取/运维", "ROLLBACK_STAGE"));
        assertEquals("处理中", IntakeDemandStatusRules.resolveNextStatus(
                "待验收", "数据提取/运维", "ROLLBACK_STAGE"));
    }
}
