package cn.aslight.workhub.service.agent;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AgentToolCatalog {
    private final Map<String, AgentTool> tools;

    public AgentToolCatalog(List<AgentTool> tools) {
        this.tools = tools.stream().collect(Collectors.toUnmodifiableMap(AgentTool::code, Function.identity()));
    }

    public AgentTool require(String code) {
        AgentTool tool = tools.get(code);
        if (tool == null) throw new IllegalArgumentException("执行工具未接入: " + code);
        return tool;
    }

    public List<AgentTool> all() {
        return tools.values().stream().sorted(java.util.Comparator.comparing(AgentTool::code)).toList();
    }
}
