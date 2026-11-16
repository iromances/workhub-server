package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.dao.intake.IntakeHistoryMapper;
import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.dao.intake.IntakeStructuredFieldMapper;
import cn.aslight.workhub.dao.intake.IntakeTodoMapper;
import cn.aslight.workhub.dao.intake.IntakeWorkItemRelationMapper;
import cn.aslight.workhub.dao.system.UserMapper;
import cn.aslight.workhub.model.intake.IntakeBoardResponse;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.system.UserOptionResponse;
import cn.aslight.workhub.service.attachment.AttachmentService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntakeServiceBoardTest {

    @Test
    void board_shouldGroupActiveRequirementsByOwnerWithTaskCountEffortAndDates() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeRecordEntity developing = boardRecord(1L, "zhangsan", "开发中");
        developing.setStructuredDataJson("""
                {"requirementName":"审批改造","approvalCode":"SP-001","businessLine":"保费分期",
                 "developmentStartedDate":"2026/10/12","plannedDevelopmentStartDate":"2026/10/10",
                 "plannedDueDate":"2026/11/01","plannedReleaseDate":"2026/11/05",
                 "actualEffort":"1d","fields":[],"attachmentSummaries":[]}
                """);
        developing.setLatestDevelopmentDraftJson("""
                {"status":"DRAFT","totalEstimatedEffort":"1.5d","developmentEstimatedEffort":"1.5d",
                 "workItems":[{"title":"任务A","estimatedEffort":"4h"},{"title":"任务B","estimatedEffort":"1d"},{"title":"任务C"}]}
                """);
        IntakeRecordEntity testing = boardRecord(2L, "zhangsan", "测试中");
        testing.setPriority("高");
        testing.setStructuredDataJson("""
                {"requirementName":"账单导出","plannedDevelopmentStartDate":"2026-10-20","plannedReleaseDate":"2026/11/08","fields":[],"attachmentSummaries":[]}
                """);
        IntakeRecordEntity pendingRelease = boardRecord(3L, "lisi", "待上线");
        pendingRelease.setRequirementType("数据提取/运维");
        IntakeRecordEntity completed = boardRecord(4L, "zhangsan", "已完成");
        IntakeRecordEntity unassigned = boardRecord(5L, null, "待澄清");
        when(intakeMapper.findAll(null))
                .thenReturn(List.of(developing, testing, pendingRelease, completed, unassigned));
        UserMapper userMapper = mock(UserMapper.class);
        when(userMapper.findActiveUsers()).thenReturn(List.of(
                new UserOptionResponse("zhangsan", "张三", null),
                new UserOptionResponse("lisi", null, null)
        ));
        IntakeService service = service(intakeMapper, userMapper);

        IntakeBoardResponse response = service.board(false, null);

        assertEquals("demandOwner", response.groupBy());
        assertEquals(false, response.includeClosed());
        assertEquals(3, response.people().size());
        assertEquals(List.of("lisi", "张三", "未指定负责人"),
                response.people().stream().map(IntakeBoardResponse.PersonBoard::displayName).toList());

        IntakeBoardResponse.PersonBoard zhangsan = response.people().get(1);
        assertEquals("zhangsan", zhangsan.userName());
        assertEquals(2, zhangsan.requirementCount());
        assertEquals("12h", zhangsan.totalEstimatedEffort());
        assertEquals(List.of(1L, 2L), zhangsan.requirements().stream()
                .map(IntakeBoardResponse.BoardRequirement::id).toList());
        IntakeBoardResponse.BoardRequirement first = zhangsan.requirements().getFirst();
        assertEquals(3, first.taskCount());
        assertEquals(3, first.ownedTaskCount());
        assertEquals("12h", first.ownedEstimatedEffort());
        assertEquals("12h", first.totalEstimatedEffort());
        assertEquals("8h", first.actualEffort());
        assertEquals("2026-10-12", first.startDate());
        assertEquals("2026-11-01", first.dueDate());
        assertEquals("保费分期", first.businessLine());
        IntakeBoardResponse.BoardRequirement second = zhangsan.requirements().getLast();
        assertEquals(0, second.taskCount());
        assertNull(second.totalEstimatedEffort());
        assertEquals("2026-10-20", second.startDate());
        assertEquals("2026-11-08", second.dueDate());

        IntakeBoardResponse.PersonBoard lisi = response.people().getFirst();
        assertEquals(1, lisi.requirementCount());
        assertNull(lisi.totalEstimatedEffort());
        assertEquals("数据提取/运维", lisi.requirements().getFirst().requirementType());

        IntakeBoardResponse.PersonBoard unassignedBoard = response.people().getLast();
        assertNull(unassignedBoard.userName());
        assertEquals("未指定负责人", unassignedBoard.displayName());
        assertEquals(1, unassignedBoard.requirementCount());
    }

    @Test
    void board_shouldIncludeClosedRequirementsWhenRequested() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeRecordEntity developing = boardRecord(1L, "zhangsan", "开发中");
        IntakeRecordEntity terminated = boardRecord(2L, "zhangsan", "终止关闭");
        when(intakeMapper.findAll(null)).thenReturn(List.of(developing, terminated));
        IntakeService service = service(intakeMapper, mock(UserMapper.class));

        assertEquals(1, service.board(false, null).people().getFirst().requirementCount());
        IntakeBoardResponse includeClosed = service.board(true, null);
        assertTrue(includeClosed.includeClosed());
        assertEquals(List.of(1L, 2L), includeClosed.people().getFirst().requirements().stream()
                .map(IntakeBoardResponse.BoardRequirement::id).toList());
    }

    @Test
    void board_shouldSkipRecordsBeforeDemandLifecycle() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeRecordEntity identifying = boardRecord(1L, "zhangsan", null);
        identifying.setEnrichmentStatus("RUNNING");
        when(intakeMapper.findAll(null)).thenReturn(List.of(identifying));
        IntakeService service = service(intakeMapper, mock(UserMapper.class));

        assertTrue(service.board(true, null).people().isEmpty());
    }

    @Test
    void board_shouldGroupByTaskItemOwnerWhenRequested() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeRecordEntity developing = boardRecord(1L, "zhangsan", "开发中");
        developing.setStructuredDataJson("""
                {"requirementName":"审批改造","plannedDueDate":"2026/11/01","fields":[],"attachmentSummaries":[]}
                """);
        developing.setLatestDevelopmentDraftJson("""
                {"status":"DRAFT","totalEstimatedEffort":"1.5d",
                 "workItems":[
                   {"title":"任务A","estimatedEffort":"4h","ownerUserName":"zhangsan"},
                   {"title":"任务B","estimatedEffort":"1d","ownerUserName":"zhangsan"},
                   {"title":"任务C","estimatedEffort":"2h","ownerUserName":"lisi"}]}
                """);
        IntakeRecordEntity unevaluated = boardRecord(2L, "lisi", "待排期");
        unevaluated.setLatestDevelopmentDraftJson("""
                {"status":"DRAFT","totalEstimatedEffort":"8h","workItems":[{"title":"任务D","estimatedEffort":"8h"}]}
                """);
        when(intakeMapper.findAll(null)).thenReturn(List.of(developing, unevaluated));
        IntakeService service = service(intakeMapper, mock(UserMapper.class));

        IntakeBoardResponse response = service.board(false, "taskOwner");

        assertEquals("taskOwner", response.groupBy());
        assertEquals(List.of("lisi", "zhangsan", "未指定负责人"), response.people().stream()
                .map(IntakeBoardResponse.PersonBoard::displayName).toList());

        IntakeBoardResponse.PersonBoard lisi = response.people().getFirst();
        assertEquals(1, lisi.requirementCount());
        assertEquals("2h", lisi.totalEstimatedEffort());
        IntakeBoardResponse.BoardRequirement lisiDeveloping = lisi.requirements().getFirst();
        assertEquals(1L, lisiDeveloping.id());
        assertEquals(3, lisiDeveloping.taskCount());
        assertEquals(1, lisiDeveloping.ownedTaskCount());
        assertEquals("2h", lisiDeveloping.ownedEstimatedEffort());
        assertEquals("12h", lisiDeveloping.totalEstimatedEffort());

        IntakeBoardResponse.PersonBoard zhangsan = response.people().get(1);
        assertEquals(1, zhangsan.requirementCount());
        assertEquals("12h", zhangsan.totalEstimatedEffort());
        assertEquals(2, zhangsan.requirements().getFirst().ownedTaskCount());
        assertEquals("12h", zhangsan.requirements().getFirst().ownedEstimatedEffort());

        // 未指派任务不再兜底给需求负责人，归入未指定负责人列
        IntakeBoardResponse.PersonBoard unassigned = response.people().getLast();
        assertEquals(1, unassigned.requirementCount());
        assertEquals("8h", unassigned.totalEstimatedEffort());
        assertEquals(2L, unassigned.requirements().getFirst().id());
        assertEquals(1, unassigned.requirements().getFirst().ownedTaskCount());
        assertEquals("8h", unassigned.requirements().getFirst().ownedEstimatedEffort());
    }

    @Test
    void board_shouldExcludeRequirementsWithoutTaskItemsInTaskOwnerView() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeRecordEntity noTasks = boardRecord(1L, "zhangsan", "待澄清");
        noTasks.setLatestDevelopmentDraftJson("{\"status\":\"DRAFT\",\"workItems\":[]}");
        IntakeRecordEntity noDraft = boardRecord(2L, "zhangsan", "已收录");
        when(intakeMapper.findAll(null)).thenReturn(List.of(noTasks, noDraft));
        IntakeService service = service(intakeMapper, mock(UserMapper.class));

        assertTrue(service.board(false, "taskOwner").people().isEmpty());
        assertEquals(2, service.board(false, null).people().getFirst().requirementCount());
    }

    @Test
    void board_shouldPutUnownedTasksIntoUnassignedColumn() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeRecordEntity developing = boardRecord(1L, "zhangsan", "开发中");
        developing.setLatestDevelopmentDraftJson("""
                {"status":"DRAFT","totalEstimatedEffort":"6h",
                 "workItems":[
                   {"title":"任务A","estimatedEffort":"4h","ownerUserName":"zhangsan"},
                   {"title":"任务B","estimatedEffort":"2h"}]}
                """);
        when(intakeMapper.findAll(null)).thenReturn(List.of(developing));
        IntakeService service = service(intakeMapper, mock(UserMapper.class));

        IntakeBoardResponse response = service.board(false, "taskOwner");

        assertEquals(List.of("zhangsan", "未指定负责人"), response.people().stream()
                .map(IntakeBoardResponse.PersonBoard::displayName).toList());
        assertEquals(1, response.people().getFirst().requirementCount());
        IntakeBoardResponse.PersonBoard unassigned = response.people().getLast();
        assertEquals(1, unassigned.requirementCount());
        assertEquals(1, unassigned.requirements().getFirst().ownedTaskCount());
        assertEquals("2h", unassigned.requirements().getFirst().ownedEstimatedEffort());
    }

    @Test
    void board_shouldRejectUnknownGroupBy() {
        IntakeService service = service(mock(IntakeMapper.class), mock(UserMapper.class));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.board(false, "unknown"));

        assertEquals("不支持的看板维度: unknown，仅支持 demandOwner / taskOwner", error.getMessage());
    }

    private IntakeService service(IntakeMapper intakeMapper, UserMapper userMapper) {
        return new IntakeService(
                intakeMapper,
                mock(IntakeHistoryMapper.class),
                mock(IntakeWorkItemRelationMapper.class),
                mock(IntakeTodoMapper.class),
                mock(IntakeStructuredFieldMapper.class),
                null,
                mock(AttachmentService.class),
                new IntakeStructuredDataExtractor(),
                new IntakeStructuredFieldNormalizer(),
                mock(IntakeEnrichmentService.class),
                mock(CodexCliSqlDraftGenerator.class),
                null,
                new ObjectMapper(),
                null,
                userMapper
        );
    }

    private IntakeRecordEntity boardRecord(Long id, String ownerUserName, String demandStatus) {
        IntakeRecordEntity entity = new IntakeRecordEntity();
        entity.setId(id);
        entity.setSourceType("需求截图附件录入");
        entity.setSourceChannel("需求录入");
        entity.setSenderName("测试用户");
        entity.setReceivedAt(LocalDateTime.of(2026, 10, 9, 10, 0));
        entity.setDevelopmentOwnerUserName(ownerUserName);
        entity.setDemandStatus(demandStatus);
        entity.setEnrichmentStatus("SUCCEEDED");
        entity.setRequirementType("研发需求");
        entity.setRequirementName("需求" + id);
        entity.setBusinessLineCode("BL000001");
        entity.setPriority("中");
        return entity;
    }
}
