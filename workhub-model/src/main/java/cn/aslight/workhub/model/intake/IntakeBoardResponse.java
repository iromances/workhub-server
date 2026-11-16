package cn.aslight.workhub.model.intake;

import java.util.List;

/**
 * 需求看板响应：按负责人维度聚合在负责的需求。
 */
public record IntakeBoardResponse(String groupBy,
                                  boolean includeClosed,
                                  List<PersonBoard> people) {

    /**
     * 看板上单个负责人的需求面板。
     */
    public record PersonBoard(String userName,
                              String displayName,
                              int requirementCount,
                              String totalEstimatedEffort,
                              List<BoardRequirement> requirements) {
    }

    /**
     * 看板上单条需求卡片。任务数取最新研发拆解草稿的任务项；
     * ownedTaskCount / ownedEstimatedEffort 为当前维度下该人负责的部分，
     * 需求负责人维度下等于整条需求的任务与工时，任务负责人维度下为该人名下任务。
     */
    public record BoardRequirement(Long id,
                                   String requirementName,
                                   String approvalCode,
                                   String demandStatus,
                                   String requirementType,
                                   String priority,
                                   String businessLine,
                                   String businessLineCode,
                                   int taskCount,
                                   String totalEstimatedEffort,
                                   String actualEffort,
                                   String startDate,
                                   String dueDate,
                                   int ownedTaskCount,
                                   String ownedEstimatedEffort) {
    }
}
