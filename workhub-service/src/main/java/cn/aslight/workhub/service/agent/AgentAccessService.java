package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.AgentMapper;
import cn.aslight.workhub.dao.project.BusinessLineMapper;
import cn.aslight.workhub.model.agent.*;
import cn.aslight.workhub.model.project.BusinessLineEntity;
import cn.aslight.workhub.service.auth.PermissionContextService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class AgentAccessService {
    private final PermissionContextService permissions;
    private final BusinessLineMapper businessLines;
    private final AgentMapper mapper;
    private final ObjectMapper json;

    public AgentAccessService(PermissionContextService permissions, BusinessLineMapper businessLines,
                              AgentMapper mapper, ObjectMapper json) {
        this.permissions = permissions;
        this.businessLines = businessLines;
        this.mapper = mapper;
        this.json = json;
    }

    public Actor actor(String userName) {
        var user = permissions.requireActiveUser(userName);
        return new Actor(user.getUserName(), Set.copyOf(permissions.permissionCodes(user.getId())));
    }

    public void requireAgent(Actor actor, AgentDefinitionEntity agent) {
        if (agent == null) throw new IllegalArgumentException("Agent 不存在");
        if (!agent.ownerName().equals(actor.name()) && !actor.has("agent:manage")) {
            throw new AccessDeniedException("无权访问该 Agent");
        }
    }

    public void validateScopes(List<AgentScope> scopes) {
        if (new HashSet<>(scopes).size() != scopes.size()) throw new IllegalArgumentException("业务线/环境范围重复");
        for (AgentScope scope : scopes) {
            BusinessLineEntity line = businessLines.findByCode(scope.businessLineCode());
            if (line == null || !Boolean.TRUE.equals(line.getEnabled())) {
                throw new IllegalArgumentException("业务线不存在或已停用: " + scope.businessLineCode());
            }
            Set<String> environments = line.getSupportedEnvironments() == null ? Set.of()
                    : new HashSet<>(Arrays.asList(line.getSupportedEnvironments().split(",")));
            if (!environments.contains(scope.environmentCode())) {
                throw new IllegalArgumentException("业务线未配置环境: " + scope.businessLineCode() + "/" + scope.environmentCode());
            }
        }
    }

    /** 未限定范围的员工默认执行范围：全部启用业务线及其已配置环境，与 /api/agents/options 的 scopes 同口径。 */
    public List<AgentScope> allEnabledScopes() {
        return businessLines.findAll(null).stream().filter(line -> Boolean.TRUE.equals(line.getEnabled()))
                .flatMap(line -> (line.getSupportedEnvironments() == null ? List.<String>of()
                        : Arrays.stream(line.getSupportedEnvironments().split(",")).map(String::trim).filter(value -> !value.isEmpty()).toList())
                        .stream().map(environmentCode -> new AgentScope(line.getBusinessLineCode(), environmentCode)))
                .toList();
    }

    /** 多步骤外部操作在每次写入前复核同一操作授权，不能只依赖入口时的权限。 */
    public void requireExecution(AgentToolContext context, String toolCode, Set<String> requiredPermissions) {
        requireRunAuthorization(context);
        AgentDefinitionEntity current = mapper.findAgent(context.agentId());
        Actor requester = actor(context.requestedBy());
        requireAgent(requester, current);
        requester.require("agent:task:assign");
        if (!current.enabled()) throw new AccessDeniedException("Agent 已停用");
        if (requiredPermissions.stream().noneMatch(requester::has) || requiredPermissions.stream().noneMatch(actor(current.ownerName())::has)) {
            throw new AccessDeniedException("无权使用工具: " + toolCode);
        }
    }

    /** 执行前重新检查账号、Agent 和所选 Provider；快照不能延续被撤销的授权。 */
    public void requireRunAuthorization(AgentToolContext context) {
        var current = mapper.findAgent(context.agentId());
        var requester = actor(context.requestedBy());
        requireAgent(requester, current);
        requester.require("agent:task:assign");
        actor(current.ownerName()).require("agent:manage");
        if (!current.enabled()) throw new AccessDeniedException("Agent 已停用");
        var configuration = json.readValue(current.configurationJson(), AgentConfiguration.class);
        if (!configuration.providerConfigId().equals(context.configuration().providerConfigId())) {
            throw new AccessDeniedException("Agent 的 AI 接入已修改，请以新配置重新运行");
        }
    }

    public void requireScope(AgentToolContext context, AgentScope scope) {
        AgentDefinitionEntity current = mapper.findAgent(context.agentId());
        if (current == null || !current.enabled()) throw new AccessDeniedException("Agent 已停用");
        AgentConfiguration currentConfig = json.readValue(current.configurationJson(), AgentConfiguration.class);
        if (!context.configuration().allowsScope(scope) || !context.specification().scopes().contains(scope)
                || !currentConfig.allowsScope(scope)) {
            throw new AccessDeniedException("业务线或环境超出本次任务授权");
        }
        validateScopes(List.of(scope));
    }

    /** 无环境字段的工作项只按真实业务线读取；不把可用任务范围当作该缺陷的发生环境。 */
    public void requireBusinessLine(AgentToolContext context, String line) {
        var current = mapper.findAgent(context.agentId());
        if (current == null || !current.enabled()) throw new AccessDeniedException("Agent 已停用");
        var configuration = json.readValue(current.configurationJson(), AgentConfiguration.class);
        var permitted = context.specification().scopes().stream().filter(scope -> line.equals(scope.businessLineCode()))
                .filter(context.configuration()::allowsScope).filter(configuration::allowsScope).toList();
        if (permitted.isEmpty()) throw new AccessDeniedException("业务线超出本次任务授权");
        validateScopes(permitted);
    }

    public record Actor(String name, Set<String> permissions) {
        public boolean has(String code) { return permissions.contains(code); }
        public void require(String code) {
            if (!has(code)) throw new AccessDeniedException("缺少权限: " + code);
        }
    }
}
