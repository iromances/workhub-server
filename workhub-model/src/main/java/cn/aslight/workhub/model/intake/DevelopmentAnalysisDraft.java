package cn.aslight.workhub.model.intake;

import java.util.List;

/**
 * 研发需求代码影响分析草稿。
 */
public record DevelopmentAnalysisDraft(String status,
                                       String projectGroup,
                                       Long projectId,
                                       String projectName,
                                       String repositoryUrl,
                                       String requirementRawPath,
                                       String requirementWikiPath,
                                       String requirementWikiUrl,
                                       String summary,
                                       List<String> requirementChangePoints,
                                       List<String> impactedModules,
                                       List<String> risks,
                                       List<String> questions,
                                       List<String> developerPool,
                                       List<DevelopmentWorkItemDraft> workItems,
                                       String totalEstimatedEffort,
                                       String developmentEstimatedEffort,
                                       String testingEstimatedEffort,
                                       String plannedDueDate,
                                       String plannedTestingStartDate,
                                       String plannedTestingEndDate,
                                       String plannedReleaseDate,
                                       String zentaoSyncStatus,
                                       String zentaoSyncMessage,
                                       String generatedAt,
                                       String generator) {
}
