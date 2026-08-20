package cn.aslight.workhub.controller.mcp;

import cn.aslight.workhub.service.mcp.McpRuntimeService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class McpRuntimeControllerTest {

    @Test
    void call_shouldReturnRawJsonRpcResponse() throws Exception {
        McpRuntimeService runtimeService = stubService(new McpRuntimeService.HttpResponse(200, """
                {"jsonrpc":"2.0","id":1,"result":{"serverInfo":{"name":"workhub-http-mcp-runtime"}}}
                """));
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

    @Test
    void call_shouldReturnAcceptedWithoutBodyForNotification() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new McpRuntimeController(
                stubService(new McpRuntimeService.HttpResponse(202, null))
        )).build();

        mockMvc.perform(post("/api/mcp/runtime")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonrpc":"2.0","method":"notifications/initialized","params":{}}
                                """))
                .andExpect(status().isAccepted());
    }

    @Test
    void call_shouldRejectBrowserOriginAndGetStream() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new McpRuntimeController(
                stubService(new McpRuntimeService.HttpResponse(200, "{}"))
        )).build();

        mockMvc.perform(post("/api/mcp/runtime")
                        .header("Origin", "https://evil.example")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/mcp/runtime"))
                .andExpect(status().isMethodNotAllowed());
    }

    private McpRuntimeService stubService(McpRuntimeService.HttpResponse response) {
        return new McpRuntimeService(null, null, null) {
            @Override
            public HttpResponse handleHttp(String rawRequest, String protocolVersionHeader) {
                return response;
            }
        };
    }
}
