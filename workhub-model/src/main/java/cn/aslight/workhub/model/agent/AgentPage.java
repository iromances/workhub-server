package cn.aslight.workhub.model.agent;

import java.util.List;

public record AgentPage<T>(long total, List<T> items) {
}
