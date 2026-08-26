package cn.aslight.workhub.model.ops;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public class SystemAlertEventBatchDeleteRequest {

    @NotEmpty(message = "请选择需要删除的系统预警事件")
    private List<@NotNull(message = "事件ID不能为空") @Positive(message = "事件ID必须大于0") Long> ids;

    public List<Long> getIds() {
        return ids;
    }

    public void setIds(List<Long> ids) {
        this.ids = ids;
    }
}
