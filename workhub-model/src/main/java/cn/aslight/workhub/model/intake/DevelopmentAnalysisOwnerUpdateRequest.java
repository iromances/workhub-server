package cn.aslight.workhub.model.intake;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 研发拆解草稿负责人更新请求。
 */
public record DevelopmentAnalysisOwnerUpdateRequest(@NotEmpty(message = "任务负责人不能为空")
                                                    List<@Valid WorkItemOwner> workItems) {

    /**
     * 单个工作项负责人。
     */
    public record WorkItemOwner(@NotNull(message = "任务序号不能为空")
                                Integer index,
                                String ownerUserName) {
    }
}
