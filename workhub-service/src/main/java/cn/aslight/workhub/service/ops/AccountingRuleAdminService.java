package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.AccountingMonitorMapper;
import cn.aslight.workhub.dao.ops.AccountingRuleMapper;
import cn.aslight.workhub.dao.ops.FuncCallSqlMapper;
import cn.aslight.workhub.model.ops.AccountingMonitorCheckEntity;
import cn.aslight.workhub.model.ops.AccountingMonitorConfigEntity;
import cn.aslight.workhub.model.ops.AccountingMonitorFamilyEntity;
import cn.aslight.workhub.model.ops.AccountingRuleModels.CheckSaveItem;
import cn.aslight.workhub.model.ops.AccountingRuleModels.FamilyCheckInput;
import cn.aslight.workhub.model.ops.AccountingRuleModels.FamilyCreateRequest;
import cn.aslight.workhub.model.ops.AccountingRuleModels.FamilyEditRequest;
import cn.aslight.workhub.model.ops.AccountingRuleModels.FamilyEnabledRequest;
import cn.aslight.workhub.model.ops.AccountingRuleModels.FamilySqlSaveRequest;
import cn.aslight.workhub.model.ops.AccountingRuleModels.ChecksSaveRequest;
import cn.aslight.workhub.model.ops.AccountingRuleModels.SqlTemplateItem;
import cn.aslight.workhub.model.ops.FuncCallSqlEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * REQ-7f26a063：业务监测规则组管理——启停、检查项保存、自定义规则组增删改、
 * 三段 SQL 模板查询与原子保存。校验全部在后端，失败整体回滚不留部分数据。
 */
@Service
public class AccountingRuleAdminService {

    private static final Pattern FAMILY_CODE = Pattern.compile("^[a-z][a-z0-9_]{1,49}$");
    private static final Pattern CHECK_ID = Pattern.compile("^[A-Z][A-Z0-9_]{0,39}$");
    private static final String SOURCE_MODULE = "OPS_ACCOUNTING_INTEGRITY";
    private static final List<String> SUFFIXES = List.of("BOUNDS", "SUMMARY", "SAMPLE");
    private static final String PARAM_DESC = "startId/endId=冻结主键边界内的批次；startDateTime/endExclusiveDateTime=NULL 或带引号日期；"
            + "ruleCode=带引号规则编码；sampleLimit=1至20。日期按来源创建时间筛选。";

    private final AccountingMonitorMapper monitors;
    private final AccountingRuleMapper rules;
    private final FuncCallSqlMapper templates;
    private final AccountingRuleInitializer initializer;

    public AccountingRuleAdminService(AccountingMonitorMapper monitors,
                                      AccountingRuleMapper rules,
                                      FuncCallSqlMapper templates,
                                      AccountingRuleInitializer initializer) {
        this.monitors = monitors;
        this.rules = rules;
        this.templates = templates;
        this.initializer = initializer;
    }

    @Transactional
    public void setEnabled(Long configId, String familyCode, FamilyEnabledRequest request, String operator) {
        AccountingMonitorFamilyEntity family = requireIntegrityFamily(configId, familyCode);
        if (Boolean.TRUE.equals(family.deleted())) {
            throw new IllegalArgumentException("已删除的规则组不能启停：" + familyCode);
        }
        if (Boolean.TRUE.equals(request.enabled()) && !hasEnabledCheck(family)) {
            throw new IllegalArgumentException("该规则组没有启用的检查项，启用前请先启用至少一个检查项");
        }
        rules.updateFamilyEnabled(family.id(), request.enabled(), operator);
    }

    @Transactional
    public void saveChecks(Long configId, String familyCode, ChecksSaveRequest request, String operator) {
        AccountingMonitorFamilyEntity family = requireIntegrityFamily(configId, familyCode);
        Map<String, AccountingMonitorCheckEntity> existing = new LinkedHashMap<>();
        for (AccountingMonitorCheckEntity check : rules.findChecksByFamily(family.id())) {
            existing.put(check.checkId(), check);
        }
        Set<String> seen = new HashSet<>();
        for (CheckSaveItem item : request.checks()) {
            if (!seen.add(item.id())) {
                throw new IllegalArgumentException("检查项重复：" + item.id());
            }
            AccountingMonitorCheckEntity check = existing.get(item.id());
            if (check == null) {
                throw new IllegalArgumentException("未知检查项：" + item.id());
            }
            rules.updateCheckState(check.id(), item.enabled(), requireSeverity(item.severity()), operator);
        }
    }

    /** 新增自定义规则组：规则组、检查项、三条模板同事务登记；任一校验失败整体回滚。 */
    @Transactional
    public void createFamily(Long configId, FamilyCreateRequest request, String operator) {
        AccountingMonitorConfigEntity config = requireIntegrityConfig(configId);
        String code = requireFamilyCode(request.code());
        if (rules.findFamily(configId, code) != null) {
            throw new IllegalArgumentException("规则组编码已被占用（含已删除）：" + code);
        }
        String name = requireName(request.name(), "规则组名称");
        String description = optionalText(request.description());
        int batchSize = requireBatchSize(request.batchSize());
        List<FamilyCheckInput> checks = requireChecks(request.checks());
        validateSql(IntegritySqlContract.sqlCode(code, "BOUNDS"), request.sql().bounds());
        validateSql(IntegritySqlContract.sqlCode(code, "SUMMARY"), request.sql().summary());
        validateSql(IntegritySqlContract.sqlCode(code, "SAMPLE"), request.sql().sample());
        for (String suffix : SUFFIXES) {
            String sqlCode = IntegritySqlContract.sqlCode(code, suffix);
            if (templates.findByCode(sqlCode) != null) {
                throw new IllegalArgumentException("SQL 编码已被占用：" + sqlCode);
            }
        }

        rules.insertFamily(new AccountingMonitorFamilyEntity(null, configId, code, name, description, batchSize,
                "CUSTOM", true, false, nextSortOrder(configId), operator, operator, null, null));
        AccountingMonitorFamilyEntity inserted = rules.findFamily(configId, code);
        if (inserted == null) {
            throw new IllegalStateException("规则组插入后未能读取：" + code);
        }
        int order = 0;
        for (FamilyCheckInput check : checks) {
            order++;
            rules.insertCheck(new AccountingMonitorCheckEntity(null, inserted.id(), check.id(), check.name(),
                    check.severity(), optionalText(check.note()), true, order, operator, operator, null, null));
        }
        for (String suffix : SUFFIXES) {
            templates.insert(template(code, name, suffix, switch (suffix) {
                case "BOUNDS" -> request.sql().bounds();
                case "SUMMARY" -> request.sql().summary();
                default -> request.sql().sample();
            }));
        }
    }

    /** 编辑自定义规则组：检查项按请求全量替换，保留项沿用原启停状态，新增默认启用，缺失物理删除。 */
    @Transactional
    public void editFamily(Long configId, String familyCode, FamilyEditRequest request, String operator) {
        AccountingMonitorFamilyEntity family = requireCustomFamily(configId, familyCode);
        String name = requireName(request.name(), "规则组名称");
        int batchSize = requireBatchSize(request.batchSize());
        List<FamilyCheckInput> checks = requireChecks(request.checks());
        rules.updateFamilyMeta(family.id(), name, optionalText(request.description()), batchSize, operator);

        Map<String, AccountingMonitorCheckEntity> existing = new LinkedHashMap<>();
        for (AccountingMonitorCheckEntity check : rules.findChecksByFamily(family.id())) {
            existing.put(check.checkId(), check);
        }
        for (String checkId : existing.keySet()) {
            if (checks.stream().noneMatch(item -> item.id().equals(checkId))) {
                rules.deleteCheck(existing.get(checkId).id());
            }
        }
        int order = 0;
        for (FamilyCheckInput input : checks) {
            order++;
            AccountingMonitorCheckEntity current = existing.get(input.id());
            if (current == null) {
                rules.insertCheck(new AccountingMonitorCheckEntity(null, family.id(), input.id(), input.name(),
                        input.severity(), optionalText(input.note()), true, order, operator, operator, null, null));
            } else {
                rules.updateCheckMeta(current.id(), input.name(), input.severity(), optionalText(input.note()), operator);
            }
        }
    }

    /** 逻辑删除自定义规则组：编码不可复用，三条模板随之停用。 */
    @Transactional
    public void deleteFamily(Long configId, String familyCode, String operator) {
        AccountingMonitorFamilyEntity family = requireCustomFamily(configId, familyCode);
        if (Boolean.TRUE.equals(family.deleted())) {
            throw new IllegalArgumentException("规则组已删除：" + familyCode);
        }
        rules.markFamilyDeleted(family.id(), operator);
        for (String suffix : SUFFIXES) {
            FuncCallSqlEntity template = templates.findByCode(IntegritySqlContract.sqlCode(familyCode, suffix));
            if (template != null && Boolean.TRUE.equals(template.getEnabled())) {
                templates.updateEnabled(template.getId(), false);
            }
        }
    }

    /** 三段模板原子保存：只更新模板文本；任一段校验失败三条都不变。 */
    @Transactional
    public void saveFamilySql(Long configId, String familyCode, FamilySqlSaveRequest request) {
        requireIntegrityFamily(configId, familyCode);
        Map<String, String> sqls = Map.of(
                "BOUNDS", request.boundsSql(),
                "SUMMARY", request.summarySql(),
                "SAMPLE", request.sampleSql());
        Map<String, FuncCallSqlEntity> loaded = new LinkedHashMap<>();
        for (String suffix : SUFFIXES) {
            String sqlCode = IntegritySqlContract.sqlCode(familyCode, suffix);
            validateSql(sqlCode, sqls.get(suffix));
            FuncCallSqlEntity template = templates.findByCode(sqlCode);
            if (template == null) {
                throw new IllegalArgumentException("SQL 模板未登记：" + sqlCode);
            }
            loaded.put(suffix, template);
        }
        for (String suffix : SUFFIXES) {
            templates.updateSqlTemplate(loaded.get(suffix).getId(), sqls.get(suffix));
        }
    }

    /** 模板查询：一致性配置按 family 取三段；流程配置取应发起（SQL 来源时）、实际、成功笔数模板。 */
    public List<SqlTemplateItem> sqlTemplates(Long configId, String family) {
        AccountingMonitorConfigEntity config = requireConfig(configId);
        if (AccountingIntegrityCatalog.TYPE.equals(config.scenarioType())) {
            String familyCode = trimToNull(family);
            if (familyCode == null) {
                throw new IllegalArgumentException("一致性配置必须指定规则组（family 参数）");
            }
            requireIntegrityFamily(configId, familyCode);
            List<SqlTemplateItem> items = new ArrayList<>();
            for (String suffix : SUFFIXES) {
                String sqlCode = IntegritySqlContract.sqlCode(familyCode, suffix);
                FuncCallSqlEntity template = templates.findByCode(sqlCode);
                items.add(item(suffixName(suffix), familyCode, suffix, sqlCode, template));
            }
            return items;
        }
        List<SqlTemplateItem> items = new ArrayList<>();
        if (trimToNull(config.expectedCountSqlCode()) != null) {
            items.add(item("应发起笔数", null, null, config.expectedCountSqlCode(),
                    templates.findByCode(config.expectedCountSqlCode())));
        }
        items.add(item("实际发起笔数", null, null, config.actualCountSqlCode(),
                templates.findByCode(config.actualCountSqlCode())));
        if (trimToNull(config.successCountSqlCode()) != null) {
            items.add(item("成功笔数", null, null, config.successCountSqlCode(),
                    templates.findByCode(config.successCountSqlCode())));
        }
        return items;
    }

    private SqlTemplateItem item(String purpose, String familyCode, String suffix, String sqlCode,
                                 FuncCallSqlEntity template) {
        if (template == null) {
            return new SqlTemplateItem(purpose, familyCode, suffix, sqlCode, null, null, null, null, null, false);
        }
        return new SqlTemplateItem(purpose, familyCode, suffix, sqlCode, template.getId(), template.getName(),
                template.getDescription(), template.getSqlTemplate(), template.getParamDesc(),
                Boolean.TRUE.equals(template.getEnabled()));
    }

    private FuncCallSqlEntity template(String familyCode, String familyName, String suffix, String sql) {
        FuncCallSqlEntity entity = new FuncCallSqlEntity();
        entity.setSqlCode(IntegritySqlContract.sqlCode(familyCode, suffix));
        entity.setScenarioCode("ACC-" + familyCode);
        entity.setBranchOrder(SUFFIXES.indexOf(suffix) + 1);
        entity.setName(familyName + "·" + suffix);
        entity.setDescription("自定义规则组「" + familyName + "」的" + suffixName(suffix) + " SQL");
        entity.setSqlTemplate(sql);
        entity.setParamDesc(PARAM_DESC);
        entity.setSourceModule(SOURCE_MODULE);
        entity.setEnabled(true);
        entity.setRemark("REQ-7f26a063：自定义规则组登记");
        return entity;
    }

    private AccountingMonitorConfigEntity requireConfig(Long configId) {
        AccountingMonitorConfigEntity config = monitors.findById(configId);
        if (config == null) {
            throw new IllegalArgumentException("业务监测配置不存在：" + configId);
        }
        return config;
    }

    private AccountingMonitorConfigEntity requireIntegrityConfig(Long configId) {
        AccountingMonitorConfigEntity config = requireConfig(configId);
        if (!AccountingIntegrityCatalog.TYPE.equals(config.scenarioType())) {
            throw new IllegalArgumentException("仅单据及支付一致性配置支持规则组管理");
        }
        return config;
    }

    private AccountingMonitorFamilyEntity requireIntegrityFamily(Long configId, String familyCode) {
        requireIntegrityConfig(configId);
        AccountingMonitorFamilyEntity family = rules.findFamily(configId, trimToNull(familyCode));
        if (family == null) {
            throw new IllegalArgumentException("规则组不存在：" + familyCode);
        }
        return family;
    }

    private AccountingMonitorFamilyEntity requireCustomFamily(Long configId, String familyCode) {
        AccountingMonitorFamilyEntity family = requireIntegrityFamily(configId, familyCode);
        if (!"CUSTOM".equals(family.sourceType())) {
            throw new IllegalArgumentException("内置规则组不支持编辑或删除：" + familyCode);
        }
        return family;
    }

    private boolean hasEnabledCheck(AccountingMonitorFamilyEntity family) {
        return rules.findChecksByFamily(family.id()).stream()
                .anyMatch(check -> Boolean.TRUE.equals(check.enabled()));
    }

    private int nextSortOrder(Long configId) {
        return rules.findFamilies(configId).stream()
                .mapToInt(AccountingMonitorFamilyEntity::sortOrder)
                .max()
                .orElse(0) + 1;
    }

    private String requireFamilyCode(String value) {
        String code = trimToNull(value);
        if (code == null || !FAMILY_CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("规则组编码必须为 2-50 位小写字母/数字/下划线，且以小写字母开头");
        }
        if (IntegritySqlContract.sqlCode(code, "SUMMARY").length() > 64) {
            throw new IllegalArgumentException("规则组编码过长：派生 SQL 编码超过 64 位");
        }
        return code;
    }

    /** v1.1：名称不得包含「·」，保证批次详情按「·」切分规则组与检查项成立。 */
    private String requireName(String value, String label) {
        String name = trimToNull(value);
        if (name == null) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        if (name.length() > 128) {
            throw new IllegalArgumentException(label + "过长（最多 128 字符）");
        }
        if (name.contains("·")) {
            throw new IllegalArgumentException(label + "不能包含「·」");
        }
        return name;
    }

    private List<FamilyCheckInput> requireChecks(List<FamilyCheckInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("至少需要一个检查项");
        }
        Set<String> seen = new HashSet<>();
        for (FamilyCheckInput input : inputs) {
            String id = trimToNull(input.id());
            if (id == null || !CHECK_ID.matcher(id).matches()) {
                throw new IllegalArgumentException("检查项 ID 必须为大写字母开头、1-40 位大写字母/数字/下划线");
            }
            requireName(input.name(), "检查项名称");
            requireSeverity(input.severity());
            if (!seen.add(id)) {
                throw new IllegalArgumentException("检查项重复：" + id);
            }
        }
        return inputs;
    }

    private String requireSeverity(String value) {
        String severity = trimToNull(value);
        if (!"WARN".equals(severity) && !"ERROR".equals(severity)) {
            throw new IllegalArgumentException("级别只支持 WARN 或 ERROR");
        }
        return severity;
    }

    private int requireBatchSize(Integer value) {
        if (value == null || value < 1 || value > 20000) {
            throw new IllegalArgumentException("批大小必须在 1 到 20000 之间");
        }
        return value;
    }

    /** 既有通用校验 + 一致性契约校验；模板长度上限与 Function Call 一致。 */
    private void validateSql(String sqlCode, String sql) {
        String template = trimToNull(sql);
        if (template == null) {
            throw new IllegalArgumentException("SQL 模板不能为空：" + sqlCode);
        }
        if (template.length() > 60_000) {
            throw new IllegalArgumentException("SQL 模板过长（最多 60000 字符）：" + sqlCode);
        }
        String stripped = template.strip().toUpperCase(Locale.ROOT);
        if (!stripped.startsWith("SELECT") && !stripped.startsWith("WITH") && !stripped.startsWith("SHOW")
                && !stripped.startsWith("DESC")) {
            throw new IllegalArgumentException("模板必须以 SELECT/WITH/SHOW/DESC 开头（只读网关约束）：" + sqlCode);
        }
        if (template.contains("%s") || template.contains("%d") || template.contains("${")) {
            throw new IllegalArgumentException("模板只支持 :name 命名参数：" + sqlCode);
        }
        IntegritySqlContract.validate(sqlCode, template);
    }

    private String suffixName(String suffix) {
        return switch (suffix) {
            case "BOUNDS" -> "边界";
            case "SUMMARY" -> "汇总";
            default -> "样本";
        };
    }

    private String optionalText(String value) {
        String text = trimToNull(value);
        if (text != null && text.length() > 512) {
            throw new IllegalArgumentException("说明过长（最多 512 字符）");
        }
        return text;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
