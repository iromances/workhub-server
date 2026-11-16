package cn.aslight.workhub.service.system;

import cn.aslight.workhub.dao.system.IntegrationApiEndpointMapper;
import cn.aslight.workhub.model.system.IntegrationApiCatalogResponse;
import cn.aslight.workhub.model.system.IntegrationSystemEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntegrationApiCatalogServiceTest {

    private final IntegrationCatalogService catalogService = mock(IntegrationCatalogService.class);
    private final IntegrationApiEndpointMapper mapper = mock(IntegrationApiEndpointMapper.class);
    private final IntegrationApiCatalogService service = new IntegrationApiCatalogService(catalogService, mapper);

    @Test
    void pageResolvesSystemAndPassesFiltersWithPaging() {
        when(catalogService.requireExisting(6L)).thenReturn(system("JENKINS"));
        when(mapper.count("JENKINS", "build", "GET", null)).thenReturn(3L);
        when(mapper.findPage(eq("JENKINS"), eq("build"), eq("GET"), isNull(), eq(0L), eq(20)))
                .thenReturn(List.of(endpoint()));
        when(mapper.groupSummaries("JENKINS")).thenReturn(List.of());
        when(mapper.latestCreatedAt("JENKINS")).thenReturn(LocalDateTime.of(2026, 11, 13, 12, 0));
        when(mapper.sourceEntries("JENKINS")).thenReturn(List.of(
                Map.of("sourceUrl", "https://www.jenkins.io/doc/book/using/remote-access-api/", "sourceNote", "官方文档整理")));

        IntegrationApiCatalogResponse response = service.page(6L, "build", "get", null, null, null);

        assertEquals("JENKINS", response.getSystemCode());
        assertEquals(3, response.getTotal());
        assertEquals(1, response.getPage());
        assertEquals(20, response.getPageSize());
        assertEquals(1, response.getItems().size());
        assertEquals("https://www.jenkins.io/doc/book/using/remote-access-api/",
                response.getSourceSummary().getSources().getFirst().getUrl());
        assertEquals(LocalDateTime.of(2026, 11, 13, 12, 0), response.getSourceSummary().getFetchedAt());
        verify(mapper).findPage(eq("JENKINS"), eq("build"), eq("GET"), isNull(), eq(0L), eq(20));
    }

    @Test
    void pageClampsPageSizeAndComputesOffset() {
        when(catalogService.requireExisting(5L)).thenReturn(system("ZENTAO"));
        when(mapper.count(eq("ZENTAO"), isNull(), isNull(), isNull())).thenReturn(0L);
        when(mapper.findPage(eq("ZENTAO"), isNull(), isNull(), isNull(), eq(200L), eq(100)))
                .thenReturn(List.of());
        when(mapper.groupSummaries("ZENTAO")).thenReturn(List.of());
        when(mapper.latestCreatedAt("ZENTAO")).thenReturn(null);
        when(mapper.sourceEntries("ZENTAO")).thenReturn(List.of());

        IntegrationApiCatalogResponse response = service.page(5L, null, null, null, 3, 500);

        assertEquals(100, response.getPageSize());
        assertEquals(3, response.getPage());
        verify(mapper).findPage(eq("ZENTAO"), isNull(), isNull(), isNull(), eq(200L), eq(100));
    }

    @Test
    void pageReturnsEmptyForSystemOutsideCatalog() {
        when(catalogService.requireExisting(7L)).thenReturn(system("GITLAB_X"));

        IntegrationApiCatalogResponse response = service.page(7L, "projects", "GET", "list", 1, 20);

        assertEquals("GITLAB_X", response.getSystemCode());
        assertEquals(0, response.getTotal());
        assertTrue(response.getItems().isEmpty());
        assertTrue(response.getResourceGroups().isEmpty());
        assertTrue(response.getSourceSummary().getSources().isEmpty());
        assertNull(response.getSourceSummary().getFetchedAt());
        verify(mapper, never()).count(anyString(), any(), any(), any());
    }

    @Test
    void pageRejectsInvalidPagingAndMethod() {
        when(catalogService.requireExisting(5L)).thenReturn(system("ZENTAO"));
        assertThrows(IllegalArgumentException.class, () -> service.page(5L, null, null, null, 0, 20));
        assertThrows(IllegalArgumentException.class, () -> service.page(5L, null, null, null, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> service.page(5L, null, "FETCH", null, 1, 20));
    }

    private static IntegrationSystemEntity system(String code) {
        var entity = new IntegrationSystemEntity();
        entity.setId(1L);
        entity.setSystemCode(code);
        entity.setSystemName(code);
        entity.setIntegrationMethod("REST_API");
        entity.setAuthType("PAT");
        entity.setEnabled(true);
        entity.setSortOrder(0);
        return entity;
    }

    private static cn.aslight.workhub.model.system.IntegrationApiEndpointEntity endpoint() {
        var entity = new cn.aslight.workhub.model.system.IntegrationApiEndpointEntity();
        entity.setSystemCode("JENKINS");
        entity.setResourceGroup("build");
        entity.setResourceGroupName("构建");
        entity.setApiName("构建详情（结果、参数、耗时）");
        entity.setHttpMethod("GET");
        entity.setApiPath("/job/{name}/{number}/api/json");
        entity.setPlatformUsed(true);
        entity.setSortOrder(10);
        return entity;
    }
}
