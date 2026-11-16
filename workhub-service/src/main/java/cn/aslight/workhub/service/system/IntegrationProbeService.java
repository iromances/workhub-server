package cn.aslight.workhub.service.system;

import cn.aslight.workhub.model.system.IntegrationProbeResponse;
import cn.aslight.workhub.model.system.IntegrationSystemEntity;
import cn.aslight.workhub.service.agent.AgentJenkinsClient;
import cn.aslight.workhub.service.agent.AgentJenkinsPolicy;
import cn.aslight.workhub.service.intake.ZentaoClient;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * 系统集成测试连接服务：按系统编码分派只读探针，返回明确成败信息，不回传凭据。
 */
@Service
public class IntegrationProbeService {

    private final ZentaoClient zentaoClient;
    private final AgentJenkinsPolicy jenkinsPolicy;
    private final AgentJenkinsClient jenkinsClient;
    private final SysConfigService sysConfigService;
    private final HttpClient http;
    private final tools.jackson.databind.ObjectMapper objectMapper;

    public IntegrationProbeService(ZentaoClient zentaoClient,
                                   AgentJenkinsPolicy jenkinsPolicy,
                                   AgentJenkinsClient jenkinsClient,
                                   SysConfigService sysConfigService,
                                   tools.jackson.databind.ObjectMapper objectMapper) {
        this.zentaoClient = zentaoClient;
        this.jenkinsPolicy = jenkinsPolicy;
        this.jenkinsClient = jenkinsClient;
        this.sysConfigService = sysConfigService;
        this.objectMapper = objectMapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public IntegrationProbeResponse probe(IntegrationSystemEntity system) {
        long start = System.currentTimeMillis();
        try {
            String message = switch (system.getSystemCode()) {
                case "ZENTAO" -> probeZentao();
                case "JENKINS" -> probeJenkins();
                case "GITLAB" -> probeGitlab();
                default -> null;
            };
            if (message == null) {
                return new IntegrationProbeResponse(false, "该系统暂不支持测试连接", null);
            }
            return new IntegrationProbeResponse(true, message, System.currentTimeMillis() - start);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return new IntegrationProbeResponse(false, ex.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private String probeZentao() {
        ZentaoClient.ProbeResult result = zentaoClient.probe();
        return "禅道连接成功（账号 " + result.account() + "，耗时 " + result.elapsedMs() + " ms）";
    }

    private String probeJenkins() {
        AgentJenkinsPolicy.Instance instance = jenkinsPolicy.firstEnabledInstance();
        String token = sysConfigService.requireSecretValue(instance.credentialGroup(), instance.tokenKey());
        var target = new AgentJenkinsClient.Target(URI.create(instance.baseUrl()), List.of("validate"),
                instance.userName(), token);
        var probe = jenkinsClient.probeInstance(target);
        String version = probe.version() == null ? "未知版本" : probe.version();
        return "Jenkins 连接成功（实例 " + instance.name() + "，版本 " + version + "）";
    }

    private String probeGitlab() {
        String webApiUrl = sysConfigService.findPlainValue("gitlab.global", "webApiUrl");
        String accessToken = sysConfigService.findPlainValue("gitlab.global", "accessToken");
        if (webApiUrl == null) {
            throw new IllegalArgumentException("GitLab 地址未配置（gitlab.global.webApiUrl）");
        }
        if (accessToken == null) {
            throw new IllegalArgumentException("GitLab 访问令牌未配置（gitlab.global.accessToken）");
        }
        HttpResponse<String> response;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(webApiUrl) + "/api/v4/user"))
                    .header("PRIVATE-TOKEN", accessToken)
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ex) {
            throw new IllegalStateException("GitLab 连接失败：" + ex.getMessage(), ex);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("GitLab 认证失败（HTTP " + response.statusCode()
                    + "），请核对 gitlab.global.accessToken");
        }
        try {
            String username = objectMapper.readTree(response.body()).path("username").asText(null);
            return "GitLab 连接成功（用户 " + (username == null ? "未知" : username) + "）";
        } catch (Exception ex) {
            throw new IllegalStateException("GitLab 响应解析失败", ex);
        }
    }

    private String trimSlash(String value) {
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
