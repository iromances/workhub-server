package cn.aslight.workhub.service.mcp;

import cn.aslight.workhub.config.McpProperties;
import cn.aslight.workhub.dao.mcp.McpResourceMapper;
import cn.aslight.workhub.dao.project.BusinessLineMapper;
import cn.aslight.workhub.dao.project.ProjectInvolvedSystemMapper;
import cn.aslight.workhub.model.mcp.McpResourceEntity;
import cn.aslight.workhub.model.mcp.McpResourceSaveRequest;
import cn.aslight.workhub.service.system.SysConfigService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class McpFileResourceServiceTest {
    private final McpResourceMapper mapper = mock(McpResourceMapper.class);
    private final McpResourceEntity entity = new McpResourceEntity();
    private final McpResourceService service = new McpResourceService(mapper, new McpCryptoService(new McpProperties()),
            mock(McpBastionService.class), mock(BusinessLineMapper.class), mock(ProjectInvolvedSystemMapper.class),
            mock(SysConfigService.class));

    @Test
    void createsResourcesWithMntAsDefaultAiReadDirectory() {
        java.util.concurrent.atomic.AtomicReference<McpResourceEntity> created = new java.util.concurrent.atomic.AtomicReference<>();
        doAnswer(invocation -> {
            McpResourceEntity value = invocation.getArgument(0);
            value.setId(2L);
            created.set(value);
            return null;
        }).when(mapper).insert(any());
        when(mapper.findById(2L)).thenAnswer(invocation -> created.get());
        assertEquals(List.of("/mnt"), service.create(request()).allowedLogPaths());
        assertEquals("[\"/mnt\"]", created.get().getAllowedLogPathsJson());
    }

    @Test
    void preservesOmittedRootsRevokesExplicitEmptyListAndPublishesCatalogField() {
        entity.setId(1L);
        entity.setTargetKey("test-files");
        entity.setAllowedLogPathsJson("[\"/mnt/etc-server\"]");
        when(mapper.findById(1L)).thenReturn(entity);
        when(mapper.findByTargetKey("test-files")).thenReturn(entity);
        when(mapper.findAll(isNull(), isNull(), isNull(), isNull(), eq(true))).thenReturn(List.of(entity));
        McpResourceSaveRequest request = request();
        assertEquals(List.of("/mnt/etc-server"), service.update(1L, request).allowedLogPaths());
        assertEquals(List.of("/mnt/etc-server"), service.catalog().serverTargets().getFirst().get("allowedLogPaths"));
        request.setAllowedLogPaths(List.of());
        assertEquals(List.of(), service.update(1L, request).allowedLogPaths());
    }

    @Test
    void normalizesRootsRejectsInvalidPathsAndKeepsDatabaseScopeEmpty() {
        entity.setId(1L);
        entity.setTargetKey("test-files");
        when(mapper.findById(1L)).thenReturn(entity);
        when(mapper.findByTargetKey("test-files")).thenReturn(entity);
        McpResourceSaveRequest request = request();
        request.setAllowedLogPaths(List.of(" /mnt/etc-server/ ", "/mnt/etc-server"));
        assertEquals(List.of("/mnt/etc-server"), service.update(1L, request).allowedLogPaths());
        for (String invalid : List.of("/", "relative", "/mnt/../etc", "/bad\npath")) {
            request.setAllowedLogPaths(List.of(invalid));
            assertThrows(IllegalArgumentException.class, () -> service.update(1L, request), invalid);
        }
        request.setAllowedLogPaths(List.of("/mnt/etc-server"));
        request.setResourceType("DATABASE");
        entity.setPasswordEncrypted("existing-encrypted-value");
        assertEquals(List.of(), service.update(1L, request).allowedLogPaths());
    }

    private McpResourceSaveRequest request() {
        McpResourceSaveRequest request = new McpResourceSaveRequest();
        request.setResourceType("SERVER");
        request.setTargetKey("test-files");
        request.setPublicResource(true);
        request.setEnvironmentCode("TEST");
        request.setName("物流测试");
        request.setHost("127.0.0.1");
        request.setPort(22);
        request.setUsername("test");
        request.setSshIdentityFile("/test/identity");
        return request;
    }
}
