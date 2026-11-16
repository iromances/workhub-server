package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.service.system.SysConfigService;
import org.springframework.stereotype.Service;

/** 平台数据指南：库表坐标等数据路由信息由平台统一维护并注入每轮运行，职责与任务描述只写业务语义。 */
@Service
public class AgentPlatformGuide {
    public static final String CONFIG_GROUP = "agent.runtime";
    public static final String CONFIG_KEY = "dataGuide";
    private static final String FALLBACK = "（未配置平台数据指南：可用 list_mcp_targets 与 get_business_line_context 发现数据库目标与上下文；"
            + "结论必须说明数据来源受限，不得虚构查询。）";

    private final SysConfigService configs;

    public AgentPlatformGuide(SysConfigService configs) {
        this.configs = configs;
    }

    public String guide(AgentToolContext context) {
        String configured = configs.findPlainValue(CONFIG_GROUP, CONFIG_KEY);
        return configured == null || configured.isBlank() ? FALLBACK : configured.strip();
    }
}
