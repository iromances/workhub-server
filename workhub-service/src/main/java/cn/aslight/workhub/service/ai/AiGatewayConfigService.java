package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.model.ai.AiGatewayConfig;
import cn.aslight.workhub.model.system.SysConfigResponse;
import cn.aslight.workhub.model.system.SysConfigSaveRequest;
import cn.aslight.workhub.service.system.SysConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** 兼容 asset 原有 /ai-config/detail、/save；新网关仍以 Provider/UseCase 为准。 */
@Service
public class AiGatewayConfigService {
    private static final String GROUP = "AI_GATEWAY_LEGACY";
    private final SysConfigService service;
    public AiGatewayConfigService(SysConfigService service) { this.service = service; }

    public AiGatewayConfig getCurrentConfig() {
        Map<String,String> values = new HashMap<>();
        for (SysConfigResponse row : service.list(GROUP, null)) values.put(row.configKey(), row.value());
        return new AiGatewayConfig("DEF", values.get("provider"), values.get("model"), values.get("reasoningLevel"),
                values.get("speedMode"), integer(values.get("timeoutSeconds")), bool(values.get("jsonSchemaEnabled")),
                values.get("cliCommand"), values.get("cliWorkingDirectory"), values.get("apiBaseUrl"),
                values.get("apiKey"), integer(values.get("connectTimeoutSeconds")), integer(values.get("readTimeoutSeconds")),
                values.get("siteUrl"), values.get("appName"));
    }

    @Transactional
    public AiGatewayConfig save(AiGatewayConfig config) {
        AiGatewayConfig value = config == null ? new AiGatewayConfig("DEF", null,null,null,null,null,null,null,null,null,null,null,null,null,null) : config;
        Map<String,String> values = new LinkedHashMap<>();
        values.put("provider", value.provider()); values.put("model", value.model());
        values.put("reasoningLevel", value.reasoningLevel()); values.put("speedMode", value.speedMode());
        values.put("timeoutSeconds", string(value.timeoutSeconds())); values.put("jsonSchemaEnabled", boolString(value.jsonSchemaEnabled()));
        values.put("cliCommand", value.cliCommand()); values.put("cliWorkingDirectory", value.cliWorkingDirectory());
        values.put("apiBaseUrl", value.apiBaseUrl()); values.put("apiKey", value.apiKey());
        values.put("connectTimeoutSeconds", string(value.connectTimeoutSeconds()));
        values.put("readTimeoutSeconds", string(value.readTimeoutSeconds())); values.put("siteUrl", value.siteUrl());
        values.put("appName", value.appName());
        Map<String,SysConfigResponse> existing = new HashMap<>();
        for (SysConfigResponse row : service.list(GROUP, null)) existing.put(row.configKey(), row);
        for (Map.Entry<String,String> entry : values.entrySet()) {
            SysConfigResponse old = existing.get(entry.getKey());
            if ("apiKey".equals(entry.getKey()) && old != null && (entry.getValue() == null
                    || "******".equals(entry.getValue()) || Objects.equals(entry.getValue(), old.value()))) continue;
            SysConfigSaveRequest request = request(entry.getKey(), entry.getValue(), "apiKey".equals(entry.getKey()));
            if (old == null) service.create(request); else service.update(old.id(), request);
        }
        return getCurrentConfig();
    }

    private SysConfigSaveRequest request(String key, String value, boolean secret) {
        SysConfigSaveRequest r = new SysConfigSaveRequest(); r.setConfigGroup(GROUP); r.setConfigKey(key);
        r.setConfigName("AI 网关兼容配置-" + key); r.setValueType(secret ? "SECRET" : "TEXT");
        r.setValue(value); r.setEnabled(true); return r;
    }
    private String string(Object value) { return value == null ? null : String.valueOf(value); }
    private String boolString(Boolean value) { return value == null ? null : (value ? "1" : "0"); }
    private Integer integer(String value) { try { return value == null || value.isBlank() ? null : Integer.valueOf(value); } catch (Exception e) { return null; } }
    private Boolean bool(String value) { return value == null || value.isBlank() ? null : ("1".equals(value) || Boolean.parseBoolean(value)); }
}
