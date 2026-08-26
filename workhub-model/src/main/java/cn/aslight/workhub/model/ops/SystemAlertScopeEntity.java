package cn.aslight.workhub.model.ops;

import java.time.LocalDateTime;
import java.util.List;

public class SystemAlertScopeEntity {

    private Long id;
    private String businessLineCode;
    private String environmentCode;
    private String watchMode;
    private Boolean enabled;
    private String remark;
    private List<SystemAlertScopeServiceEntity> services = List.of();
    private List<String> indexPatterns = List.of();
    private String indexPatternExpression;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBusinessLineCode() { return businessLineCode; }
    public void setBusinessLineCode(String businessLineCode) { this.businessLineCode = businessLineCode; }
    public String getEnvironmentCode() { return environmentCode; }
    public void setEnvironmentCode(String environmentCode) { this.environmentCode = environmentCode; }
    public String getWatchMode() { return watchMode; }
    public void setWatchMode(String watchMode) { this.watchMode = watchMode; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public List<SystemAlertScopeServiceEntity> getServices() { return services; }
    public void setServices(List<SystemAlertScopeServiceEntity> services) {
        this.services = services == null ? List.of() : List.copyOf(services);
    }
    public List<String> getIndexPatterns() { return indexPatterns; }
    public void setIndexPatterns(List<String> indexPatterns) {
        this.indexPatterns = indexPatterns == null ? List.of() : List.copyOf(indexPatterns);
    }
    public String getIndexPatternExpression() { return indexPatternExpression; }
    public void setIndexPatternExpression(String indexPatternExpression) { this.indexPatternExpression = indexPatternExpression; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
