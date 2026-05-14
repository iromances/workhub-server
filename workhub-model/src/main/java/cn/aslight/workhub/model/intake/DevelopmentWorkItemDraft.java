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
                                       String risk) {
}
