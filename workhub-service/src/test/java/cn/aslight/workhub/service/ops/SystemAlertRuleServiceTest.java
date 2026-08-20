package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.SystemAlertRuleMapper;
import cn.aslight.workhub.model.ops.SystemAlertRuleEntity;
import cn.aslight.workhub.model.ops.SystemAlertRuleKeywordEntity;
import cn.aslight.workhub.model.ops.SystemAlertRuleSaveRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SystemAlertRuleServiceTest {

    @Test
    void createShouldNormalizeEnumsAndPersistUniqueKeywordsInOrder() {
        FakeMapper mapper = new FakeMapper();
        SystemAlertRuleService service = new SystemAlertRuleService(mapper);
        SystemAlertRuleSaveRequest request = request();
        request.setKeywords(List.of("余额不足", " lastPacketReceivedIdleMillis ", "余额不足"));

        var response = service.create(request);

        assertEquals("IGNORE", response.action());
        assertEquals("MESSAGE", response.matchScope());
        assertEquals("ANY", response.matchMode());
        assertEquals(List.of("余额不足", "lastPacketReceivedIdleMillis"), response.keywords());
    }

    @Test
    void updateShouldReplaceExistingKeywords() {
        FakeMapper mapper = new FakeMapper();
        SystemAlertRuleService service = new SystemAlertRuleService(mapper);
        var created = service.create(request());
        SystemAlertRuleSaveRequest update = request();
        update.setRuleName("更新后的规则");
        update.setKeywords(List.of("新关键词一", "新关键词二"));

        var response = service.update(created.id(), update);

        assertEquals("更新后的规则", response.ruleName());
        assertEquals(List.of("新关键词一", "新关键词二"), response.keywords());
    }

    @Test
    void createShouldRejectInvalidActionAndEmptyKeywords() {
        SystemAlertRuleService service = new SystemAlertRuleService(new FakeMapper());
        SystemAlertRuleSaveRequest invalidAction = request();
        invalidAction.setAction("UNKNOWN");
        SystemAlertRuleSaveRequest emptyKeywords = request();
        emptyKeywords.setKeywords(List.of());

        assertThrows(IllegalArgumentException.class, () -> service.create(invalidAction));
        assertThrows(IllegalArgumentException.class, () -> service.create(emptyKeywords));
    }

    private SystemAlertRuleSaveRequest request() {
        SystemAlertRuleSaveRequest request = new SystemAlertRuleSaveRequest();
        request.setRuleName("测试过滤规则");
        request.setAction("ignore");
        request.setMatchScope("message");
        request.setMatchMode("any");
        request.setKeywords(List.of("余额不足"));
        request.setPriority(100);
        request.setEnabled(true);
        request.setRemark("测试");
        return request;
    }

    private static final class FakeMapper implements SystemAlertRuleMapper {
        private SystemAlertRuleEntity stored;
        private final List<SystemAlertRuleKeywordEntity> keywords = new ArrayList<>();

        @Override
        public List<SystemAlertRuleEntity> findRules(boolean enabledOnly, String keyword) {
            return stored == null ? List.of() : List.of(stored);
        }

        @Override
        public SystemAlertRuleEntity findById(Long id) {
            return stored != null && id.equals(stored.getId()) ? stored : null;
        }

        @Override
        public SystemAlertRuleEntity findByName(String ruleName) {
            return stored != null && ruleName.equals(stored.getRuleName()) ? stored : null;
        }

        @Override
        public List<SystemAlertRuleKeywordEntity> findKeywords(List<Long> ruleIds) {
            return keywords.stream().filter(row -> ruleIds.contains(row.ruleId())).toList();
        }

        @Override
        public int insertRule(SystemAlertRuleEntity entity) {
            entity.setId(7L);
            stored = entity;
            return 1;
        }

        @Override
        public int updateRule(SystemAlertRuleEntity entity) {
            stored = entity;
            return 1;
        }

        @Override
        public int insertKeyword(Long ruleId, String keyword, int sortOrder) {
            keywords.add(new SystemAlertRuleKeywordEntity(
                    (long) keywords.size() + 1, ruleId, keyword, sortOrder));
            return 1;
        }

        @Override
        public int deleteKeywords(Long ruleId) {
            int before = keywords.size();
            keywords.removeIf(row -> ruleId.equals(row.ruleId()));
            return before - keywords.size();
        }

        @Override
        public int deleteRule(Long id) {
            if (stored == null || !id.equals(stored.getId())) return 0;
            stored = null;
            return 1;
        }
    }
}
