package cn.aslight.workhub.model.intake;

import java.util.List;

/**
 * 研发任务同步禅道结果：新建/更新/取消逐项回执与整体状态。
 */
public record DevelopmentZentaoSyncResponse(Long intakeId,
                                            String syncStatus,
                                            String summary,
                                            List<ZentaoSyncItemResult> items) {

    /** action：CREATED / UPDATED / CANCELLED / FAILED。 */
    public record ZentaoSyncItemResult(String title,
                                       String zentaoTaskId,
                                       String zentaoTaskUrl,
                                       String action,
                                       String error) {
    }
}
