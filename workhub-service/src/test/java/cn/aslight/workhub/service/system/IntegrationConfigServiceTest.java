package cn.aslight.workhub.service.system;

import cn.aslight.workhub.dao.system.SysConfigMapper;
import cn.aslight.workhub.model.system.IntegrationConfigResponse;
import cn.aslight.workhub.model.system.IntegrationConfigSaveRequest;
import cn.aslight.workhub.model.system.IntegrationSystemEntity;
import cn.aslight.workhub.model.system.SysConfigItemEntity;
import cn.aslight.workhub.model.system.SysConfigSaveRequest;
import cn.aslight.workhub.service.agent.AgentJenkinsPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntegrationConfigServiceTest {

    private static final String POLICY_JSON = """
            {"instances":[{"id":"i1","name":"主实例","baseUrl":"http://jenkins.example","userName":"deploy",
            "credentialGroup":"jenkins.credential.i1","tokenKey":"apiToken","enabled":true}],
            "bindings":[{"id":"b1","name":"构建测试","instanceId":"i1","stage":"BUILD_TEST","jobSegments":["job"],
            "commitParameters":{"repo":"SHA"},"correlationParameter":"REQ","manifestParameter":"MANIFEST",
            "environmentParameter":"ENV","fixedParameters":{},"receiptPath":"receipt.json","enabled":true}]}
            """;

    @Test
    void getZentaoReturnsAccountWithoutPasswordValue() {
        var sysConfigService = mock(SysConfigService.class);
        when(sysConfigService.findPlainValue("zentao.base", "baseUrl")).thenReturn("http://zentao.example");
        when(sysConfigService.findPlainValue("zentao.base", "account")).thenReturn("admin");
        var service = newService(sysConfigService, mapperWithSecret("zentao.base", "password"), mock(AgentJenkinsPolicy.class));

        IntegrationConfigResponse response = service.get(zentaoId());

        assertEquals("ZENTAO", response.getSystemCode());
        assertEquals("http://zentao.example", response.getZentao().getBaseUrl());
        assertEquals("admin", response.getZentao().getAccount());
        assertTrue(response.getZentao().getPasswordConfigured());
        assertNull(response.getJenkins());
    }

    @Test
    void saveZentaoKeepsBlankPasswordAndSkipsSecretUpsert() {
        var sysConfigService = mock(SysConfigService.class);
        var jenkinsPolicy = mock(AgentJenkinsPolicy.class);
        var service = newService(sysConfigService, mapperWithSecret("zentao.base", "password"), jenkinsPolicy);
        var request = new IntegrationConfigSaveRequest();
        var zentao = new IntegrationConfigSaveRequest.ZentaoSave();
        zentao.setBaseUrl("http://zentao.example");
        zentao.setAccount("admin");
        request.setZentao(zentao);

        service.save(zentaoId(), request, "sh", "127.0.0.1");

        var captor = ArgumentCaptor.forClass(SysConfigSaveRequest.class);
        verify(sysConfigService, times(2)).create(captor.capture(), eq("sh"), eq("127.0.0.1"));
        assertTrue(captor.getAllValues().stream().noneMatch(item -> "SECRET".equals(item.getValueType())));
        verify(sysConfigService, never()).update(any(), any(), any(), any());
        verify(jenkinsPolicy, never()).current();
    }

    @Test
    void saveZentaoRejectsBlankPasswordWhenNeverConfigured() {
        var service = newService(mock(SysConfigService.class), mock(SysConfigMapper.class), mock(AgentJenkinsPolicy.class));
        var request = new IntegrationConfigSaveRequest();
        var zentao = new IntegrationConfigSaveRequest.ZentaoSave();
        zentao.setBaseUrl("http://zentao.example");
        zentao.setAccount("admin");
        request.setZentao(zentao);

        var ex = assertThrows(IllegalArgumentException.class, () -> service.save(zentaoId(), request, "sh", null));
        assertTrue(ex.getMessage().contains("首次保存必须填写密码"));
    }

    @Test
    void saveZentaoRejectsMissingSection() {
        var service = newService(mock(SysConfigService.class), mock(SysConfigMapper.class), mock(AgentJenkinsPolicy.class));
        assertThrows(IllegalArgumentException.class, () -> service.save(zentaoId(), new IntegrationConfigSaveRequest(), "sh", null));
    }

    @Test
    void getJenkinsListsInstancesWithTokenConfiguredFlag() {
        var sysConfigService = mock(SysConfigService.class);
        when(sysConfigService.findPlainValue("agent.jenkins", "policy")).thenReturn(POLICY_JSON);
        var service = newService(sysConfigService, mapperWithSecret("jenkins.credential.i1", "apiToken"), mock(AgentJenkinsPolicy.class));

        IntegrationConfigResponse response = service.get(jenkinsId());

        assertEquals("JENKINS", response.getSystemCode());
        assertNull(response.getZentao());
        assertEquals(1, response.getJenkins().getInstances().size());
        var instance = response.getJenkins().getInstances().getFirst();
        assertEquals("i1", instance.getId());
        assertEquals("deploy", instance.getUserName());
        assertTrue(instance.getTokenConfigured());
        assertTrue(instance.getEnabled());
    }

    @Test
    void getJenkinsReturnsEmptyWhenPolicyMissing() {
        var service = newService(mock(SysConfigService.class), mock(SysConfigMapper.class), mock(AgentJenkinsPolicy.class));
        var response = service.get(jenkinsId());
        assertTrue(response.getJenkins().getInstances().isEmpty());
    }

    @Test
    void saveJenkinsReplacesInstancesAndKeepsBindingsViaPolicyUpdate() {
        var sysConfigService = mock(SysConfigService.class);
        when(sysConfigService.findPlainValue("agent.jenkins", "policy")).thenReturn(POLICY_JSON);
        var mapper = mock(SysConfigMapper.class);
        when(mapper.findAll("agent.jenkins", null)).thenReturn(List.of(policyItem()));
        when(mapper.findAll("jenkins.credential.i1", null)).thenReturn(List.of(secret("jenkins.credential.i1", "apiToken")));
        var jenkinsPolicy = mock(AgentJenkinsPolicy.class);
        var service = newService(sysConfigService, mapper, jenkinsPolicy);
        var request = new IntegrationConfigSaveRequest();
        request.setJenkins(jenkinsSave(List.of(instance("i1", "主实例", null))));

        service.save(jenkinsId(), request, "sh", null);

        var captor = ArgumentCaptor.forClass(SysConfigSaveRequest.class);
        verify(sysConfigService).update(eq(9L), captor.capture(), eq("sh"), eq((String) null));
        assertTrue(captor.getValue().getValue().contains("\"bindings\""));
        assertTrue(captor.getValue().getValue().contains("\"b1\""));
        assertTrue(captor.getValue().getValue().contains("\"i1\""));
        verify(jenkinsPolicy).current();
    }

    @Test
    void saveJenkinsRejectsDeletingInstanceReferencedByBinding() {
        var sysConfigService = mock(SysConfigService.class);
        when(sysConfigService.findPlainValue("agent.jenkins", "policy")).thenReturn(POLICY_JSON);
        var service = newService(sysConfigService, mock(SysConfigMapper.class), mock(AgentJenkinsPolicy.class));
        var request = new IntegrationConfigSaveRequest();
        request.setJenkins(jenkinsSave(List.of(instance("i2", "新实例", "token"))));

        var ex = assertThrows(IllegalArgumentException.class, () -> service.save(jenkinsId(), request, "sh", null));
        assertTrue(ex.getMessage().contains("仍被 Job 绑定"));
    }

    @Test
    void saveJenkinsRequiresTokenForNewInstance() {
        var sysConfigService = mock(SysConfigService.class);
        when(sysConfigService.findPlainValue("agent.jenkins", "policy")).thenReturn(POLICY_JSON);
        var service = newService(sysConfigService, mock(SysConfigMapper.class), mock(AgentJenkinsPolicy.class));
        var request = new IntegrationConfigSaveRequest();
        request.setJenkins(jenkinsSave(List.of(
                instance("i1", "主实例", null),
                instance("i2", "新实例", null))));

        var ex = assertThrows(IllegalArgumentException.class, () -> service.save(jenkinsId(), request, "sh", null));
        assertTrue(ex.getMessage().contains("必须填写 API Token"));
    }

    @Test
    void saveJenkinsCreatesSecretForNewInstance() {
        var sysConfigService = mock(SysConfigService.class);
        when(sysConfigService.findPlainValue("agent.jenkins", "policy")).thenReturn(POLICY_JSON);
        var service = newService(sysConfigService, mock(SysConfigMapper.class), mock(AgentJenkinsPolicy.class));
        var request = new IntegrationConfigSaveRequest();
        request.setJenkins(jenkinsSave(List.of(
                instance("i1", "主实例", null),
                instance("i2", "新实例", "new-token"))));

        service.save(jenkinsId(), request, "sh", null);

        var captor = ArgumentCaptor.forClass(SysConfigSaveRequest.class);
        verify(sysConfigService, times(2)).create(captor.capture(), eq("sh"), eq((String) null));
        var secret = captor.getAllValues().stream()
                .filter(item -> "SECRET".equals(item.getValueType()))
                .findFirst().orElseThrow();
        assertEquals("jenkins.credential.i2", secret.getConfigGroup());
        assertEquals("apiToken", secret.getConfigKey());
        assertEquals("new-token", secret.getValue());
    }

    @Test
    void getRejectsUnsupportedSystemCode() {
        var service = newService(mock(SysConfigService.class), mock(SysConfigMapper.class), mock(AgentJenkinsPolicy.class));
        var ex = assertThrows(IllegalArgumentException.class, () -> service.get(7L));
        assertTrue(ex.getMessage().contains("不支持连接配置表单"));
    }

    // ---------------- fixtures ----------------

    private static IntegrationConfigService newService(SysConfigService sysConfigService,
                                                       SysConfigMapper sysConfigMapper,
                                                       AgentJenkinsPolicy jenkinsPolicy) {
        var catalogService = mock(IntegrationCatalogService.class);
        when(catalogService.requireExisting(zentaoId())).thenReturn(system(zentaoId(), "ZENTAO"));
        when(catalogService.requireExisting(jenkinsId())).thenReturn(system(jenkinsId(), "JENKINS"));
        when(catalogService.requireExisting(7L)).thenReturn(system(7L, "GITLAB"));
        return new IntegrationConfigService(catalogService, sysConfigService, sysConfigMapper,
                jenkinsPolicy, mock(SystemAuditService.class), new tools.jackson.databind.ObjectMapper());
    }

    private static Long zentaoId() {
        return 5L;
    }

    private static Long jenkinsId() {
        return 6L;
    }

    private static IntegrationSystemEntity system(Long id, String code) {
        var entity = new IntegrationSystemEntity();
        entity.setId(id);
        entity.setSystemCode(code);
        entity.setSystemName(code);
        entity.setIntegrationMethod("REST_API");
        entity.setAuthType("ACCOUNT_PASSWORD");
        entity.setEnabled(true);
        entity.setSortOrder(0);
        return entity;
    }

    private static SysConfigMapper mapperWithSecret(String group, String key) {
        var mapper = mock(SysConfigMapper.class);
        when(mapper.findAll(group, null)).thenReturn(List.of(secret(group, key)));
        return mapper;
    }

    private static SysConfigItemEntity secret(String group, String key) {
        var entity = new SysConfigItemEntity();
        entity.setId(10L);
        entity.setConfigGroup(group);
        entity.setConfigKey(key);
        entity.setValueType("SECRET");
        entity.setEnabled(true);
        entity.setEncryptedValue("cipher");
        return entity;
    }

    private static SysConfigItemEntity policyItem() {
        var entity = new SysConfigItemEntity();
        entity.setId(9L);
        entity.setConfigGroup("agent.jenkins");
        entity.setConfigKey("policy");
        entity.setValueType("TEXT");
        entity.setEnabled(true);
        entity.setPlainValue(POLICY_JSON);
        return entity;
    }

    private static IntegrationConfigSaveRequest.JenkinsSave jenkinsSave(List<IntegrationConfigSaveRequest.JenkinsInstanceSave> instances) {
        var save = new IntegrationConfigSaveRequest.JenkinsSave();
        save.setInstances(instances);
        return save;
    }

    private static IntegrationConfigSaveRequest.JenkinsInstanceSave instance(String id, String name, String apiToken) {
        var save = new IntegrationConfigSaveRequest.JenkinsInstanceSave();
        save.setId(id);
        save.setName(name);
        save.setBaseUrl("http://jenkins.example");
        save.setUserName("deploy");
        save.setApiToken(apiToken);
        save.setEnabled(true);
        return save;
    }
}
