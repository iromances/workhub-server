package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.SystemAlertRuleMapper;
import cn.aslight.workhub.model.ops.SystemAlertRuleEntity;
import cn.aslight.workhub.model.ops.SystemAlertRuleKeywordEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Service
public class SystemAlertCleanupRuleService {

    private static final int RULE_PRIORITY = 95;
    private final SystemAlertRuleMapper ruleMapper;

    public SystemAlertCleanupRuleService(SystemAlertRuleMapper ruleMapper) {
        this.ruleMapper = ruleMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long ensureIgnoreRule(String keyword) {
        SystemAlertRuleEntity compatible = ruleMapper.findCompatibleIgnoreRuleByKeyword(keyword);
        if (compatible != null) {
            ruleMapper.enableRule(compatible.getId());
            return compatible.getId();
        }

        String ruleName = automaticRuleName(keyword);
        SystemAlertRuleEntity entity = new SystemAlertRuleEntity();
        entity.setRuleName(ruleName);
        entity.setAction("IGNORE");
        entity.setMatchScope("MESSAGE");
        entity.setMatchMode("ANY");
        entity.setPriority(RULE_PRIORITY);
        entity.setEnabled(true);
        entity.setRemark("由系统预警“删除&过滤”自动创建");
        int inserted = ruleMapper.insertRuleIgnore(entity);
        SystemAlertRuleEntity stored = inserted == 1 ? entity : ruleMapper.findByName(ruleName);
        if (stored == null || stored.getId() == null) {
            throw new IllegalStateException("过滤规则创建失败");
        }
        if (inserted == 0 && !isAutomaticCompatibleRule(stored, keyword)) {
            throw new IllegalStateException("自动过滤规则名称冲突，请在过滤规则页面处理");
        }
        ruleMapper.insertKeywordIgnore(stored.getId(), keyword, 0);
        ruleMapper.enableRule(stored.getId());
        return stored.getId();
    }

    private boolean isAutomaticCompatibleRule(SystemAlertRuleEntity rule, String keyword) {
        if (!"IGNORE".equals(rule.getAction())
                || !"MESSAGE".equals(rule.getMatchScope())
                || !"ANY".equals(rule.getMatchMode())) {
            return false;
        }
        List<String> keywords = ruleMapper.findKeywords(List.of(rule.getId())).stream()
                .map(SystemAlertRuleKeywordEntity::keyword)
                .toList();
        return keywords.contains(keyword);
    }

    private String automaticRuleName(String keyword) {
        String normalizedPreview = keyword.replaceAll("\\s+", " ");
        String preview = normalizedPreview.length() > 80
                ? normalizedPreview.substring(0, 80)
                : normalizedPreview;
        String hash = UUID.nameUUIDFromBytes(keyword.getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", "")
                .substring(0, 12);
        return "删除&过滤-" + preview + "-" + hash;
    }
}
