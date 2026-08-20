package cn.aslight.workhub.model.ops;

import java.util.List;

public class SystemAlertRuleSaveRequest {
    private String ruleName;
    private String action;
    private String matchScope;
    private String matchMode;
    private List<String> keywords;
    private Integer priority;
    private Boolean enabled;
    private String remark;

    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getMatchScope() { return matchScope; }
    public void setMatchScope(String matchScope) { this.matchScope = matchScope; }
    public String getMatchMode() { return matchMode; }
    public void setMatchMode(String matchMode) { this.matchMode = matchMode; }
    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
