package cn.aslight.workhub.model.system;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 官方 API 接口目录分页响应：条目 + 资源组汇总 + 来源摘要；数据为官方文档快照，只读。
 */
public class IntegrationApiCatalogResponse {

    private String systemCode;
    private long total;
    private int page;
    private int pageSize;
    private List<IntegrationApiEndpointEntity> items;
    private List<IntegrationApiGroupSummary> resourceGroups;
    private SourceSummary sourceSummary;

    public String getSystemCode() {
        return systemCode;
    }

    public void setSystemCode(String systemCode) {
        this.systemCode = systemCode;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public List<IntegrationApiEndpointEntity> getItems() {
        return items;
    }

    public void setItems(List<IntegrationApiEndpointEntity> items) {
        this.items = items;
    }

    public List<IntegrationApiGroupSummary> getResourceGroups() {
        return resourceGroups;
    }

    public void setResourceGroups(List<IntegrationApiGroupSummary> resourceGroups) {
        this.resourceGroups = resourceGroups;
    }

    public SourceSummary getSourceSummary() {
        return sourceSummary;
    }

    public void setSourceSummary(SourceSummary sourceSummary) {
        this.sourceSummary = sourceSummary;
    }

    /** 来源摘要：抓取落库时间与去重后的来源文档口径。 */
    public static class SourceSummary {

        private LocalDateTime fetchedAt;
        private List<SourceEntry> sources;

        public LocalDateTime getFetchedAt() {
            return fetchedAt;
        }

        public void setFetchedAt(LocalDateTime fetchedAt) {
            this.fetchedAt = fetchedAt;
        }

        public List<SourceEntry> getSources() {
            return sources;
        }

        public void setSources(List<SourceEntry> sources) {
            this.sources = sources;
        }
    }

    /** 单个来源文档：地址与口径备注。 */
    public static class SourceEntry {

        private String url;
        private String note;

        public SourceEntry() {
        }

        public SourceEntry(String url, String note) {
            this.url = url;
            this.note = note;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }
    }
}
