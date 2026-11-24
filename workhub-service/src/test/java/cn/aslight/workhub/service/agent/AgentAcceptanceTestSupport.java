package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.*;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import static org.mockito.Mockito.mock;

/** 未准备代码的场景仍经过真实验收；空领域仓库不能满足代码产物。 */
final class AgentAcceptanceTestSupport {
    static AgentRunAcceptanceService withoutCode(AgentMapper agents, AgentAccessService access, ObjectMapper json, Clock clock) {
        return new AgentRunAcceptanceService(agents, mock(AgentChangeMapper.class), mock(AgentDeliveryMapper.class),
                new org.springframework.beans.factory.ObjectProvider<AgentChangeService>() {
                    @Override public AgentChangeService getObject() { throw new AssertionError("没有清单时不应读取代码详情"); }
                }, mock(AgentBuildTestEvidence.class), access, json, clock, mock(AgentNativeDeliveryService.class));
    }
}
