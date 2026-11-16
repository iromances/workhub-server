package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.AgentMapper;
import cn.aslight.workhub.dao.agent.AgentToolMapper;
import cn.aslight.workhub.model.agent.AgentToolAdminResponse;
import cn.aslight.workhub.model.agent.AgentToolRegistrationEntity;
import cn.aslight.workhub.model.agent.AgentToolSaveRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 工具管理：以代码目录为权威，pm_agent_tool 仅维护人读名称与描述；AI 运行时目录不经本服务。 */
@Service
public class AgentToolAdminService {
    private final AgentToolCatalog tools;
    private final AgentToolMapper registrations;
    private final AgentAccessService access;
    private final AgentMapper audit;
    private final ObjectMapper json;
    private final Clock clock;

    @Autowired
    public AgentToolAdminService(AgentToolCatalog tools, AgentToolMapper registrations,
                                 AgentAccessService access, AgentMapper audit, ObjectMapper json) {
        this(tools, registrations, access, audit, json, Clock.systemUTC());
    }

    AgentToolAdminService(AgentToolCatalog tools, AgentToolMapper registrations, AgentAccessService access,
                          AgentMapper audit, ObjectMapper json, Clock clock) {
        this.tools = tools;
        this.registrations = registrations;
        this.access = access;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
    }

    public List<AgentToolAdminResponse> list(String user) {
        access.actor(user).require("agent:view");
        Map<String, AgentToolRegistrationEntity> rows = registrations.list().stream()
                .collect(Collectors.toMap(AgentToolRegistrationEntity::code, Function.identity()));
        List<AgentToolAdminResponse> result = new ArrayList<>();
        for (AgentTool tool : tools.all()) {
            AgentToolRegistrationEntity row = rows.remove(tool.code());
            result.add(entry(tool.code(), tool.readOnly(), tool.permissions(),
                    row == null ? tool.code() : row.name(), row == null ? "" : row.description(),
                    row != null, false, row == null ? 0 : row.version()));
        }
        rows.values().forEach(row -> result.add(entry(row.code(), false, List.of(), row.name(), row.description(),
                true, true, row.version())));
        result.sort(Comparator.comparing(AgentToolAdminResponse::code));
        return result;
    }

    public AgentToolAdminResponse update(String code, AgentToolSaveRequest request, String user) {
        var actor = access.actor(user);
        actor.require("agent:manage");
        AgentTool tool = tools.require(code);
        AgentToolRegistrationEntity existing = registrations.find(code);
        String name = request.name().trim();
        String description = request.description().trim();
        AgentToolRegistrationEntity saved;
        if (existing == null) {
            requireVersion(0, request.expectedVersion());
            checkChanged(registrations.insert(new AgentToolRegistrationEntity(code, name, description,
                    0, actor.name(), now(), now())));
        } else {
            requireVersion(existing.version(), request.expectedVersion());
            checkChanged(registrations.update(new AgentToolRegistrationEntity(code, name, description,
                    existing.version(), actor.name(), existing.createdAt(), now())));
        }
        saved = registrations.find(code);
        audit(code, actor.name(), existing, saved);
        return entry(code, tool.readOnly(), tool.permissions(), saved.name(), saved.description(), true, false, saved.version());
    }

    private AgentToolAdminResponse entry(String code, boolean readOnly, Collection<String> permissions, String name,
                                        String description, boolean maintained, boolean retired, long version) {
        return new AgentToolAdminResponse(code, name, description, readOnly, List.copyOf(permissions), maintained, retired, version);
    }

    private void audit(String code, String actor, AgentToolRegistrationEntity before, AgentToolRegistrationEntity after) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("before", before == null ? null
                : Map.of("name", before.name(), "description", before.description()));
        payload.put("after", Map.of("name", after.name(), "description", after.description()));
        audit.insertAudit(UUID.randomUUID().toString(), "AGENT_TOOL", code, "UPDATE", actor,
                json.writeValueAsString(payload), now());
    }

    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private static void requireVersion(long actual, Long expected) {
        if (expected == null || actual != expected) throw new IllegalStateException("版本已变化，请刷新后重试");
    }
    private static void checkChanged(int rows) {
        if (rows != 1) throw new IllegalStateException("状态已变化，请刷新后重试");
    }
}
