package cn.aslight.workhub.service.ops;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

/** 已验证的账务一致性 SQL 目录；模板种子只补缺，不覆盖页面维护内容。 */
public final class AccountingIntegrityCatalog {
    public static final String TYPE = "ACCOUNTING_INTEGRITY";
    private static final List<Family> FAMILIES = load();

    private AccountingIntegrityCatalog() {
    }

    public static List<Family> families(String businessLineCode) {
        return FAMILIES.stream().filter(f -> f.businessLineCode().equals(businessLineCode)).toList();
    }

    public static List<FuncCallSqlSeeds.Seed> seeds() {
        return FAMILIES.stream().flatMap(f -> List.of(
                seed(f, "BOUNDS", f.boundsSql(), 1),
                seed(f, "SUMMARY", f.summarySql(), 2),
                seed(f, "SAMPLE", f.sampleSql(), 3)).stream()).toList();
    }

    private static FuncCallSqlSeeds.Seed seed(Family f, String suffix, String sql, int order) {
        return new FuncCallSqlSeeds.Seed(f.sqlCode(suffix), "ACC-" + f.code(), order,
                f.name() + "·" + suffix, f.description(), sql,
                "startId/endId=冻结主键边界内的批次；startDateTime/endExclusiveDateTime=NULL 或带引号日期；"
                        + "ruleCode=带引号规则编码；sampleLimit=1至20。日期按来源创建时间筛选。",
                "OPS_ACCOUNTING_INTEGRITY");
    }

    private static List<Family> load() {
        try (var stream = AccountingIntegrityCatalog.class.getResourceAsStream("/accounting/integrity-rules.json")) {
            if (stream == null) {
                throw new IllegalStateException("账务一致性规则资源缺失");
            }
            return List.copyOf(new ObjectMapper().readValue(stream, new TypeReference<List<Family>>() { }));
        } catch (IOException ex) {
            throw new IllegalStateException("账务一致性规则资源无效", ex);
        }
    }

    public record Family(String code, String businessLineCode, String name, String description,
                         int batchSize, String boundsSql, String summarySql, String sampleSql,
                         List<Check> checks) {
        public String sqlCode(String suffix) {
            return "AI_" + code.toUpperCase(java.util.Locale.ROOT) + "_" + suffix;
        }
    }

    public record Check(String id, String name, String severity, String note) {
    }
}
