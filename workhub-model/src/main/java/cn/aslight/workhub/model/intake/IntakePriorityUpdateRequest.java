package cn.aslight.workhub.model.intake;

/**
 * 需求优先级更新请求。
 */
public class IntakePriorityUpdateRequest {

    /**
     * 优先级，支持高、中、低。
     */
    private String priority;

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }
}
