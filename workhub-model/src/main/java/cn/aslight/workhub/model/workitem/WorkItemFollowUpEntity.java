package cn.aslight.workhub.model.workitem;

/**
 * 工作项跟踪记录实体。
 */
public class WorkItemFollowUpEntity {

    /**
     * 跟踪记录主键 ID。
     */
    private Long id;
    /**
     * 所属工作项 ID。
     */
    private Long workItemId;
    /**
     * 跟踪内容。
     */
    private String content;
    /**
     * 操作人用户名。
     */
    private String operatorUserName;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getWorkItemId() {
        return workItemId;
    }

    public void setWorkItemId(Long workItemId) {
        this.workItemId = workItemId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getOperatorUserName() {
        return operatorUserName;
    }

    public void setOperatorUserName(String operatorUserName) {
        this.operatorUserName = operatorUserName;
    }
}
