package cn.aslight.workhub.model.ops;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** REQ-7f26a063：业务监测规则组管理的请求与响应模型。 */
public final class AccountingRuleModels {

    private AccountingRuleModels() {
    }

    public record FamilyEnabledRequest(@NotNull Boolean enabled) {
    }

    public record CheckSaveItem(@NotBlank String id, @NotNull Boolean enabled, @NotBlank String severity) {
    }

    public record ChecksSaveRequest(@NotEmpty @Valid List<CheckSaveItem> checks) {
    }

    /** 新增自定义规则组时的检查项输入。 */
    public record FamilyCheckInput(@NotBlank @Size(max = 64) String id,
                                   @NotBlank @Size(max = 128) String name,
                                   @NotBlank String severity,
                                   @Size(max = 512) String note) {
    }

    /** 新增自定义规则组时的三段 SQL。 */
    public record FamilySqlInput(@NotBlank String bounds,
                                 @NotBlank String summary,
                                 @NotBlank String sample) {
    }

    public record FamilyCreateRequest(@NotBlank @Size(max = 64) String code,
                                      @NotBlank @Size(max = 128) String name,
                                      @Size(max = 512) String description,
                                      @NotNull Integer batchSize,
                                      @NotEmpty @Valid List<FamilyCheckInput> checks,
                                      @NotNull @Valid FamilySqlInput sql) {
    }

    /** 编辑自定义规则组：检查项按请求全量替换，保留项沿用原启停状态。 */
    public record FamilyEditRequest(@NotBlank @Size(max = 128) String name,
                                    @Size(max = 512) String description,
                                    @NotNull Integer batchSize,
                                    @NotEmpty @Valid List<FamilyCheckInput> checks) {
    }

    /** 三段模板原子保存。 */
    public record FamilySqlSaveRequest(@NotBlank String boundsSql,
                                       @NotBlank String summarySql,
                                       @NotBlank String sampleSql) {
    }

    /** sql-templates 接口条目：编码未登记时 templateId 与 sqlTemplate 为空、enabled=false。 */
    public record SqlTemplateItem(String purpose,
                                  String familyCode,
                                  String suffix,
                                  String sqlCode,
                                  Long templateId,
                                  String name,
                                  String description,
                                  String sqlTemplate,
                                  String paramDesc,
                                  boolean enabled) {
    }
}
