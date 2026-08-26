package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.SystemAlertRuleMapper;
import cn.aslight.workhub.model.ops.SystemAlertRuleEntity;
import cn.aslight.workhub.model.ops.SystemAlertRuleKeywordEntity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SystemAlertCleanupRuleServiceTest {

    @Test
    void ensureIgnoreRule_shouldReuseAndEnableCompatibleRule() {
        FakeRuleMapper mapper = new FakeRuleMapper();
        mapper.compatible = rule(26L, "已有规则");
        SystemAlertCleanupRuleService service = new SystemAlertCleanupRuleService(mapper);

        Long ruleId = service.ensureIgnoreRule("Insert Person Time");

        assertEquals(26L, ruleId);
        assertEquals(List.of(26L), mapper.enabledRuleIds);
        assertEquals(0, mapper.insertCount);
    }

    @Test
    void ensureIgnoreRule_shouldCreateDeterministicEnabledIgnoreRule() {
        FakeRuleMapper mapper = new FakeRuleMapper();
        SystemAlertCleanupRuleService service = new SystemAlertCleanupRuleService(mapper);

        Long ruleId = service.ensureIgnoreRule("Insert Person Time");

        assertEquals(27L, ruleId);
        assertEquals("IGNORE", mapper.stored.getAction());
        assertEquals("MESSAGE", mapper.stored.getMatchScope());
        assertEquals("ANY", mapper.stored.getMatchMode());
        assertEquals(95, mapper.stored.getPriority());
        assertEquals(true, mapper.stored.getEnabled());
        assertEquals(List.of("Insert Person Time"),
                mapper.keywords.stream().map(SystemAlertRuleKeywordEntity::keyword).toList());
    }

    private SystemAlertRuleEntity rule(Long id, String name) {
        SystemAlertRuleEntity rule = new SystemAlertRuleEntity();
        rule.setId(id);
        rule.setRuleName(name);
        rule.setAction("IGNORE");
        rule.setMatchScope("MESSAGE");
        rule.setMatchMode("ANY");
        rule.setEnabled(false);
        return rule;
    }

    private static class FakeRuleMapper implements SystemAlertRuleMapper {
        private SystemAlertRuleEntity compatible;
        private SystemAlertRuleEntity stored;
        private final List<SystemAlertRuleKeywordEntity> keywords = new ArrayList<>();
        private final List<Long> enabledRuleIds = new ArrayList<>();
        private int insertCount;

        @Override public List<SystemAlertRuleEntity> findRules(boolean enabledOnly, String keyword) { return List.of(); }
        @Override public SystemAlertRuleEntity findById(Long id) { return stored; }
        @Override public SystemAlertRuleEntity findByName(String ruleName) { return stored; }
        @Override public SystemAlertRuleEntity findCompatibleIgnoreRuleByKeyword(String keyword) { return compatible; }
        @Override public List<SystemAlertRuleKeywordEntity> findKeywords(List<Long> ruleIds) { return keywords; }
        @Override public int insertRule(SystemAlertRuleEntity entity) { return insertRuleIgnore(entity); }
        @Override public int insertRuleIgnore(SystemAlertRuleEntity entity) {
            insertCount++;
            entity.setId(27L);
            stored = entity;
            return 1;
        }
        @Override public int enableRule(Long id) { enabledRuleIds.add(id); return 1; }
        @Override public int updateRule(SystemAlertRuleEntity entity) { stored = entity; return 1; }
        @Override public int insertKeyword(Long ruleId, String keyword, int sortOrder) {
            return insertKeywordIgnore(ruleId, keyword, sortOrder);
        }
        @Override public int insertKeywordIgnore(Long ruleId, String keyword, int sortOrder) {
            keywords.add(new SystemAlertRuleKeywordEntity(1L, ruleId, keyword, sortOrder));
            return 1;
        }
        @Override public int deleteKeywords(Long ruleId) { return 0; }
        @Override public int deleteRule(Long id) { return 0; }
    }
}
