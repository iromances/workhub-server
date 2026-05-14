package cn.aslight.workhub.model.intake;

import java.util.List;

/**
 * 数据运维类需求的 SQL 草稿。
 *
 * @param dialect       SQL 方言，例如 MySQL
 * @param sql           AI 生成的 SQL 草稿，不由系统执行
 * @param explanation   SQL 用途说明
 * @param parameters    需要人工替换或确认的参数说明
 * @param assumptions   AI 推断时采用的假设
 * @param questions     表结构或业务口径不明确时需要向需求方确认的问题
 * @param riskWarnings  人工执行前需要关注的风险提示
 * @param generatedAt   草稿生成时间
 * @param generator     生成来源，例如 Codex CLI
 */
public record IntakeSqlDraft(String dialect,
                             String sql,
                             String explanation,
                             List<String> parameters,
                             List<String> assumptions,
                             List<String> questions,
                             List<String> riskWarnings,
                             String generatedAt,
                             String generator) {
}
