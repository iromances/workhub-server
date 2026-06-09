package cn.aslight.workhub.model.intake;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 研发拆解草稿人工调整请求。
 */
public record DevelopmentAnalysisDraftUpdateRequest(String businessLine,
                                                    String totalEstimatedEffort,
                                                    String developmentEstimatedEffort,
                                                    String testingEstimatedEffort,
                                                    String plannedDueDate,
                                                    String plannedDevelopmentStartDate,
                                                    String plannedTestingStartDate,
                                                    String plannedTestingEndDate,
                                                    String plannedReleaseDate,
                                                    @NotNull(message = "工作项草稿不能为空")
                                                    List<@Valid WorkItemDraft> workItems) {

    /**
     * 单个研发工作项草稿。
     */
    public record WorkItemDraft(String title,
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
}
