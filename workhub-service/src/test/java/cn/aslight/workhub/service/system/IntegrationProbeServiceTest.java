package cn.aslight.workhub.service.system;

import cn.aslight.workhub.model.system.IntegrationProbeResponse;
import cn.aslight.workhub.model.system.IntegrationSystemEntity;
import cn.aslight.workhub.service.agent.AgentJenkinsClient;
import cn.aslight.workhub.service.agent.AgentJenkinsPolicy;
import cn.aslight.workhub.service.intake.ZentaoClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntegrationProbeServiceTest {

    @Test
    void zentaoProbeReportsSuccessWithAccount() {
        var zentao = mock(ZentaoClient.class);
        when(zentao.probe()).thenReturn(new ZentaoClient.ProbeResult("zhangsan", 120));
        var response = newService(zentao, mock(AgentJenkinsPolicy.class), mock(AgentJenkinsClient.class),
                mock(SysConfigService.class)).probe(system("ZENTAO"));
        assertTrue(response.isOk());
        assertTrue(response.getMessage().contains("zhangsan"));
    }

    @Test
    void zentaoProbeFailureReturnsExplicitMessage() {
        var zentao = mock(ZentaoClient.class);
        when(zentao.probe()).thenThrow(new IllegalStateException("禅道账号认证失败（HTTP 401），请核对 zentao.base.account/password"));
        var response = newService(zentao, mock(AgentJenkinsPolicy.class), mock(AgentJenkinsClient.class),
                mock(SysConfigService.class)).probe(system("ZENTAO"));
        assertFalse(response.isOk());
        assertTrue(response.getMessage().contains("HTTP 401"));
    }

    @Test
    void jenkinsProbeReportsMissingInstanceExplicitly() {
        var policy = mock(AgentJenkinsPolicy.class);
        when(policy.firstEnabledInstance()).thenThrow(new IllegalArgumentException("Jenkins 尚无启用的实例（agent.jenkins/policy）"));
        var response = newService(mock(ZentaoClient.class), policy, mock(AgentJenkinsClient.class),
                mock(SysConfigService.class)).probe(system("JENKINS"));
        assertFalse(response.isOk());
        assertTrue(response.getMessage().contains("agent.jenkins"));
    }

    @Test
    void jenkinsProbeUsesSecretCredentialAndReportsVersion() {
        var policy = mock(AgentJenkinsPolicy.class);
        when(policy.firstEnabledInstance()).thenReturn(new AgentJenkinsPolicy.Instance(
                "main", "主实例", "http://jenkins.internal/", "ci-user", "agent.jenkins", "apiToken", true));
        var configs = mock(SysConfigService.class);
        when(configs.requireSecretValue("agent.jenkins", "apiToken")).thenReturn("secret-token");
        var client = mock(AgentJenkinsClient.class);
        when(client.probeInstance(any())).thenReturn(new AgentJenkinsClient.InstanceProbe("2.440.3"));
        var response = newService(mock(ZentaoClient.class), policy, client, configs).probe(system("JENKINS"));
        assertTrue(response.isOk());
        assertTrue(response.getMessage().contains("2.440.3"));
        assertTrue(response.getMessage().contains("主实例"));
    }

    @Test
    void unsupportedSystemProbeIsExplicitlyRejected() {
        var response = newService().probe(system("SONARQUBE"));
        assertFalse(response.isOk());
        assertEquals("该系统暂不支持测试连接", response.getMessage());
    }

    @Test
    void gitlabProbeReportsMissingConfiguration() {
        var configs = mock(SysConfigService.class);
        when(configs.findPlainValue("gitlab.global", "webApiUrl")).thenReturn(null);
        var response = newService(configs).probe(system("GITLAB"));
        assertFalse(response.isOk());
        assertTrue(response.getMessage().contains("gitlab.global.webApiUrl"));
    }

    @Test
    void gitlabProbeCallsCurrentUserEndpoint() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.createContext("/api/v4/user", exchange -> {
            if (!"token-value".equals(exchange.getRequestHeaders().getFirst("PRIVATE-TOKEN"))) {
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
                return;
            }
            byte[] body = "{\"username\":\"ci-bot\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        try {
            var configs = mock(SysConfigService.class);
            when(configs.findPlainValue("gitlab.global", "webApiUrl"))
                    .thenReturn("http://127.0.0.1:" + server.getAddress().getPort());
            when(configs.findPlainValue("gitlab.global", "accessToken")).thenReturn("token-value");
            var response = newService(configs).probe(system("GITLAB"));
            assertTrue(response.isOk(), response.getMessage());
            assertTrue(response.getMessage().contains("ci-bot"));
        } finally {
            server.stop(0);
        }
    }

    private static IntegrationProbeService newService() {
        return newService(mock(ZentaoClient.class), mock(AgentJenkinsPolicy.class),
                mock(AgentJenkinsClient.class), mock(SysConfigService.class));
    }

    private static IntegrationProbeService newService(SysConfigService configs) {
        return newService(mock(ZentaoClient.class), mock(AgentJenkinsPolicy.class),
                mock(AgentJenkinsClient.class), configs);
    }

    private static IntegrationProbeService newService(ZentaoClient zentao,
                                                      AgentJenkinsPolicy policy,
                                                      AgentJenkinsClient client,
                                                      SysConfigService configs) {
        return new IntegrationProbeService(zentao, policy, client, configs, new ObjectMapper());
    }

    private static IntegrationSystemEntity system(String code) {
        var entity = new IntegrationSystemEntity();
        entity.setSystemCode(code);
        entity.setSystemName(code);
        return entity;
    }
}
