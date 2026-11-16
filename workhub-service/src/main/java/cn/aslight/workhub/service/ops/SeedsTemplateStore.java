package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.model.ops.FuncCallSqlEntity;

import java.util.Comparator;
import java.util.List;

/**
 * REQ-9f3c2a84：以 FuncCallSqlSeeds 为数据源的模板存取。生产环境使用 DB 存取
 * （FunctionCallService.DbTemplateStore，页面维护优先）；本实现供单元测试与
 * 无库环境构造 Collector 使用，保证与登记种子逐字一致。
 */
class SeedsTemplateStore implements FunctionCallService.TemplateStore {

    @Override
    public FuncCallSqlEntity byCode(String sqlCode) {
        return FuncCallSqlSeeds.SEEDS.stream()
                .filter(seed -> seed.sqlCode().equals(sqlCode))
                .findFirst()
                .map(FuncCallSqlSeeds.Seed::toEntity)
                .orElse(null);
    }

    @Override
    public List<FuncCallSqlEntity> byScenario(String scenarioCode) {
        return FuncCallSqlSeeds.SEEDS.stream()
                .filter(seed -> scenarioCode.equals(seed.scenarioCode()))
                .sorted(Comparator.comparingInt(FuncCallSqlSeeds.Seed::branchOrder))
                .map(FuncCallSqlSeeds.Seed::toEntity)
                .toList();
    }
}
