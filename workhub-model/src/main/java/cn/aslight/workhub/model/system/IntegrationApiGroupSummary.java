package cn.aslight.workhub.model.system;

/**
 * 官方 API 接口目录资源组汇总行（MyBatis 聚合映射）。
 */
public class IntegrationApiGroupSummary {

    private String resourceGroup;
    private String resourceGroupName;
    private Long endpointCount;
    private Long platformUsedCount;
    private Integer sortOrder;

    public String getResourceGroup() {
        return resourceGroup;
    }

    public void setResourceGroup(String resourceGroup) {
        this.resourceGroup = resourceGroup;
    }

    public String getResourceGroupName() {
        return resourceGroupName;
    }

    public void setResourceGroupName(String resourceGroupName) {
        this.resourceGroupName = resourceGroupName;
    }

    public Long getEndpointCount() {
        return endpointCount;
    }

    public void setEndpointCount(Long endpointCount) {
        this.endpointCount = endpointCount;
    }

    public Long getPlatformUsedCount() {
        return platformUsedCount;
    }

    public void setPlatformUsedCount(Long platformUsedCount) {
        this.platformUsedCount = platformUsedCount;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
