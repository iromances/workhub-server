package cn.aslight.workhub.model.ops;

import java.util.List;

/** REQ-9f3c2a84：Function Call 页面 DTO。 */
public final class FuncCallModels {

    private FuncCallModels() {
    }

    public record FuncCallSqlItem(Long id, String sqlCode, String scenarioCode, Integer branchOrder,
                                  String name, String description, String sqlTemplate, String paramDesc,
                                  String sourceModule, boolean enabled, String remark) {
    }

    public record FuncCallSqlSaveRequest(String sqlCode, String scenarioCode, Integer branchOrder, String name,
                                         String description, String sqlTemplate, String paramDesc,
                                         String sourceModule, Boolean enabled, String remark) {
    }

    public record FuncCallEnabledUpdateRequest(Boolean enabled) {
    }

    public record FuncCallExecuteRequest(String codeOrScenario, java.util.Map<String, String> params,
                                         String businessLineCode, String environmentCode) {
    }

    public record FuncCallExecuteResponse(String sqlCode, List<String> columns,
                                          List<java.util.Map<String, Object>> rows, String sqlRendered) {
    }

    /** REQ-b4d7e6f2：执行审计条目与分页。 */
    public record FuncCallAuditItem(Long id, String sqlCode, String scenarioCode, String source, String operator,
                                    String businessLineCode, String environmentCode, String renderedSql,
                                    String status, Integer rowCount, Long durationMs, String errorMessage,
                                    String startedAt, String finishedAt) {
    }

    public record FuncCallAuditPage(long total, int page, int pageSize, List<FuncCallAuditItem> items) {
    }
}
