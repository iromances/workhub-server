package cn.aslight.workhub.service.system;

import cn.aslight.workhub.dao.system.IntegrationApiEndpointMapper;
import cn.aslight.workhub.model.system.IntegrationApiCatalogResponse;
import cn.aslight.workhub.model.system.IntegrationSystemEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 系统集成官方 API 接口目录查询服务：只读分页、过滤与来源摘要。
 * 数据为官方文档快照（迁移种子维护），不提供任何写入入口。
 */
@Service
public class IntegrationApiCatalogService {

    /** 目录当前覆盖的集成系统（与迁移种子一致）。 */
    private static final Set<String> CATALOG_SYSTEM_CODES = Set.of("ZENTAO", "JENKINS", "GITLAB");
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> HTTP_METHODS = Set.of("GET", "POST", "PUT", "DELETE", "PATCH");

    private final IntegrationCatalogService catalogService;
    private final IntegrationApiEndpointMapper mapper;

    public IntegrationApiCatalogService(IntegrationCatalogService catalogService,
                                        IntegrationApiEndpointMapper mapper) {
        this.catalogService = catalogService;
        this.mapper = mapper;
    }

    /**
     * 分页查询某集成系统的官方接口目录；非目录覆盖系统返回空集（不报错）。
     */
    public IntegrationApiCatalogResponse page(Long systemId,
                                              String resourceGroup,
                                              String httpMethod,
                                              String keyword,
                                              Integer page,
                                              Integer pageSize) {
        IntegrationSystemEntity system = catalogService.requireExisting(systemId);
        IntegrationApiCatalogResponse response = new IntegrationApiCatalogResponse();
        response.setSystemCode(system.getSystemCode());
        int effectivePage = page == null ? 1 : page;
        int effectivePageSize = pageSize == null ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        if (effectivePage < 1) {
            throw new IllegalArgumentException("页码必须为正整数");
        }
        if (effectivePageSize < 1) {
            throw new IllegalArgumentException("每页条数必须为正整数（上限 " + MAX_PAGE_SIZE + "）");
        }
        String normalizedMethod = normalizeMethod(httpMethod);
        String normalizedGroup = trimToNull(resourceGroup);
        String normalizedKeyword = trimToNull(keyword);
        response.setPage(effectivePage);
        response.setPageSize(effectivePageSize);

        if (!CATALOG_SYSTEM_CODES.contains(system.getSystemCode())) {
            response.setTotal(0);
            response.setItems(List.of());
            response.setResourceGroups(List.of());
            response.setSourceSummary(emptySummary());
            return response;
        }
        response.setTotal(mapper.count(system.getSystemCode(), normalizedGroup, normalizedMethod, normalizedKeyword));
        response.setItems(mapper.findPage(system.getSystemCode(), normalizedGroup, normalizedMethod, normalizedKeyword,
                (long) (effectivePage - 1) * effectivePageSize, effectivePageSize));
        response.setResourceGroups(mapper.groupSummaries(system.getSystemCode()));
        response.setSourceSummary(sourceSummary(system.getSystemCode()));
        return response;
    }

    private IntegrationApiCatalogResponse.SourceSummary sourceSummary(String systemCode) {
        IntegrationApiCatalogResponse.SourceSummary summary = new IntegrationApiCatalogResponse.SourceSummary();
        summary.setFetchedAt(mapper.latestCreatedAt(systemCode));
        summary.setSources(mapper.sourceEntries(systemCode).stream()
                .map(entry -> new IntegrationApiCatalogResponse.SourceEntry(
                        String.valueOf(entry.get("sourceUrl")),
                        entry.get("sourceNote") == null ? null : String.valueOf(entry.get("sourceNote"))))
                .toList());
        return summary;
    }

    private IntegrationApiCatalogResponse.SourceSummary emptySummary() {
        IntegrationApiCatalogResponse.SourceSummary summary = new IntegrationApiCatalogResponse.SourceSummary();
        summary.setSources(List.of());
        return summary;
    }

    private String normalizeMethod(String httpMethod) {
        String normalized = trimToNull(httpMethod);
        if (normalized == null) {
            return null;
        }
        String upper = normalized.toUpperCase();
        if (!HTTP_METHODS.contains(upper)) {
            throw new IllegalArgumentException("HTTP 方法只支持 GET/POST/PUT/DELETE/PATCH");
        }
        return upper;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
