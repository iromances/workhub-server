package cn.aslight.workhub.controller.mcp;

import cn.aslight.workhub.model.mcp.McpBastionConnectionTestRequest;
import cn.aslight.workhub.model.mcp.McpBastionConnectionTestResponse;
import cn.aslight.workhub.model.mcp.McpDatabaseConnectionTestRequest;
import cn.aslight.workhub.model.mcp.McpDatabaseConnectionTestResponse;
import cn.aslight.workhub.model.mcp.McpAuditEntryResponse;
import cn.aslight.workhub.model.mcp.McpAuditPageResponse;
import cn.aslight.workhub.model.mcp.McpServerConnectionTestRequest;
import cn.aslight.workhub.model.mcp.McpServerConnectionTestResponse;
import cn.aslight.workhub.service.mcp.McpBastionService;
import cn.aslight.workhub.service.mcp.McpDatabaseConnectionTestService;
import cn.aslight.workhub.service.mcp.McpResourceService;
import cn.aslight.workhub.service.mcp.McpServerConnectionTestService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class McpResourceControllerTest {

    @Test
    void auditEntries_shouldExposePagedResult() throws Exception {
        McpResourceService resourceService = mock(McpResourceService.class);
        when(resourceService.auditEntries(2, 20)).thenReturn(new McpAuditPageResponse(
                45,
                List.of(new McpAuditEntryResponse(Map.of(
                        "timestamp", "2026-07-21T10:00:03Z",
                        "tool", "database_query"
                )))
        ));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new McpResourceController(resourceService, mock(McpBastionService.class),
                        mock(McpDatabaseConnectionTestService.class), mock(McpServerConnectionTestService.class))
        ).build();

        mockMvc.perform(get("/api/mcp/audits")
                        .param("pageNum", "2")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(45))
                .andExpect(jsonPath("$.data.items[0].fields.tool").value("database_query"));
    }

    @Test
    void testBastionConnection_shouldExposeSafeResult() throws Exception {
        McpResourceService resourceService = mock(McpResourceService.class);
        McpBastionService bastionService = mock(McpBastionService.class);
        when(bastionService.testConnection(any(McpBastionConnectionTestRequest.class), isNull(), any()))
                .thenReturn(new McpBastionConnectionTestResponse(
                        true,
                        42L,
                        "连接成功",
                        LocalDateTime.of(2026, 7, 20, 16, 0)
                ));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new McpResourceController(resourceService, bastionService,
                        mock(McpDatabaseConnectionTestService.class), mock(McpServerConnectionTestService.class))
        ).build();

        mockMvc.perform(post("/api/mcp/bastions/test-connection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "host": "10.10.0.8",
                                  "port": 22,
                                  "username": "workhub",
                                  "password": "plain-password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.durationMs").value(42))
                .andExpect(jsonPath("$.data.message").value("连接成功"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void testDatabaseConnection_shouldExposeRouteAndSafeResult() throws Exception {
        McpResourceService resourceService = mock(McpResourceService.class);
        McpBastionService bastionService = mock(McpBastionService.class);
        McpDatabaseConnectionTestService databaseTestService = mock(McpDatabaseConnectionTestService.class);
        when(databaseTestService.test(any(McpDatabaseConnectionTestRequest.class), isNull(), any()))
                .thenReturn(new McpDatabaseConnectionTestResponse(
                        true, "BASTION", null, "生产堡垒机", 76L,
                        "经“生产堡垒机”连接数据库成功", LocalDateTime.of(2026, 7, 21, 12, 0)
                ));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new McpResourceController(resourceService, bastionService, databaseTestService,
                        mock(McpServerConnectionTestService.class))
        ).build();

        mockMvc.perform(post("/api/mcp/resources/test-database-connection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "host": "db.internal",
                                  "port": 3306,
                                  "databaseSchema": "asset",
                                  "username": "readonly",
                                  "password": "plain-password",
                                  "sshBastionEnabled": true,
                                  "bastionId": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.connectionMode").value("BASTION"))
                .andExpect(jsonPath("$.data.bastionName").value("生产堡垒机"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void testServerConnection_shouldExposeRouteAndSafeResult() throws Exception {
        McpResourceService resourceService = mock(McpResourceService.class);
        McpBastionService bastionService = mock(McpBastionService.class);
        McpServerConnectionTestService serverTestService = mock(McpServerConnectionTestService.class);
        when(serverTestService.test(any(McpServerConnectionTestRequest.class), isNull(), any()))
                .thenReturn(new McpServerConnectionTestResponse(
                        true, "BASTION", null, 65L,
                        "经堡垒机连接服务器成功", LocalDateTime.of(2026, 7, 21, 16, 0)
                ));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new McpResourceController(resourceService, bastionService,
                        mock(McpDatabaseConnectionTestService.class), serverTestService)
        ).build();

        mockMvc.perform(post("/api/mcp/resources/test-server-connection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "host": "server.internal",
                                  "port": 22,
                                  "username": "workhub",
                                  "sshPassword": "plain-password",
                                  "sshBastionEnabled": true,
                                  "sshBastionHost": "jump.internal",
                                  "sshBastionPort": 22,
                                  "sshBastionUser": "jump-user",
                                  "sshBastionPassword": "jump-password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.connectionMode").value("BASTION"))
                .andExpect(jsonPath("$.data.message").value("经堡垒机连接服务器成功"))
                .andExpect(jsonPath("$.data.sshPassword").doesNotExist())
                .andExpect(jsonPath("$.data.sshBastionPassword").doesNotExist());
    }
}
