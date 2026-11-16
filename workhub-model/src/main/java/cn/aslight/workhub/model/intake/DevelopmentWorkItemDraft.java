package cn.aslight.workhub.model.intake;

import java.util.List;

/**
 * 研发拆解工作项草稿。
 */
public record DevelopmentWorkItemDraft(String title,
                                       String description,
                                       String requirementChangePoint,
                                       String taskType,
                                       List<String> targetResources,
                                       List<String> changePoints,
                                       List<String> systemTags,
                                       List<String> evidenceRefs,
                                       String confidence,
                                       String moduleName,
                                       List<String> relatedFiles,
                                       String estimatedEffort,
                                       String ownerUserName,
                                       String priority,
                                       String plannedStartDate,
                                       String plannedEndDate,
                                       String dependency,
                                       String risk,
                                       String zentaoTaskId,
                                       String zentaoTaskUrl) {

    /** 兼容旧调用点：未同步禅道的任务无映射字段。 */
    public DevelopmentWorkItemDraft(String title,
                                    String description,
                                    String requirementChangePoint,
                                    String taskType,
                                    List<String> targetResources,
                                    List<String> changePoints,
                                    List<String> systemTags,
                                    List<String> evidenceRefs,
                                    String confidence,
                                    String moduleName,
                                    List<String> relatedFiles,
                                    String estimatedEffort,
                                    String ownerUserName,
                                    String priority,
                                    String plannedStartDate,
                                    String plannedEndDate,
                                    String dependency,
                                    String risk) {
        this(title, description, requirementChangePoint, taskType, targetResources, changePoints, systemTags,
                evidenceRefs, confidence, moduleName, relatedFiles, estimatedEffort, ownerUserName, priority,
                plannedStartDate, plannedEndDate, dependency, risk, null, null);
    }
}
