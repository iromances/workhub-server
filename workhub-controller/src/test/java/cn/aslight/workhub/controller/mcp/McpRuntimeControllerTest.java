package cn.aslight.workhub.controller.mcp;

import cn.aslight.workhub.service.mcp.McpRuntimeService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class McpRuntimeControllerTest {

    @Test
    void call_shouldReturnRawJsonRpcResponse() throws Exception {
        McpRuntimeService runtimeService = mock(McpRuntimeService.class);
        when(runtimeService.handle(anyString())).thenReturn("""
                {"jsonrpc":"2.0","id":1,"result":{"serverInfo":{"name":"workhub-http-mcp-runtime"}}}
                """);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new McpRuntimeController(runtimeService)).build();

        mockMvc.perform(post("/api/mcp/runtime")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jsonrpc").value("2.0"))
                .andExpect(jsonPath("$.result.serverInfo.name").value("workhub-http-mcp-runtime"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }
}
