package cn.aslight.workhub.model.ops;

import java.util.List;

/**
 * REQ-d227e09d：账务监测场景类型清单（配置页下拉与运行维度说明）。
 */
public record AccountingScenarioTypeResponse(String type,
                                             String name,
                                             String description,
                                             List<AccountingScenarioDimensionResponse> dimensions) {
}
