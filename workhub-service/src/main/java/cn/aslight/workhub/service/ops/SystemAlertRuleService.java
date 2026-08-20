package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.SystemAlertRuleMapper;
import cn.aslight.workhub.model.ops.SystemAlertRuleAction;
import cn.aslight.workhub.model.ops.SystemAlertRuleEntity;
import cn.aslight.workhub.model.ops.SystemAlertRuleKeywordEntity;
import cn.aslight.workhub.model.ops.SystemAlertRuleMatchMode;
import cn.aslight.workhub.model.ops.SystemAlertRuleMatchScope;
import cn.aslight.workhub.model.ops.SystemAlertRuleResponse;
import cn.aslight.workhub.model.ops.SystemAlertRuleSaveRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SystemAlertRuleService {

    private final SystemAlertRuleMapper ruleMapper;

    public SystemAlertRuleService(SystemAlertRuleMapper ruleMapper) {
        this.ruleMapper = ruleMapper;
    }

    public List<SystemAlertRuleResponse> list(boolean enabledOnly, String keyword) {
        return attachKeywords(ruleMapper.findRules(enabledOnly, trimToNull(keyword))).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SystemAlertRuleResponse create(SystemAlertRuleSaveRequest request) {
        SystemAlertRuleEntity entity = toEntity(new SystemAlertRuleEntity(), request);
        if (ruleMapper.findByName(entity.getRuleName()) != null) {
            throw new IllegalArgumentException("过滤规则名称已存在");
        }
        ruleMapper.insertRule(entity);
        replaceKeywords(entity.getId(), entity.getKeywords());
        return toResponse(requireRule(entity.getId()));
    }

    @Transactional
    public SystemAlertRuleResponse update(Long id, SystemAlertRuleSaveRequest request) {
        requireRule(id);
        SystemAlertRuleEntity entity = toEntity(new SystemAlertRuleEntity(), request);
        SystemAlertRuleEntity duplicate = ruleMapper.findByName(entity.getRuleName());
        if (duplicate != null && !id.equals(duplicate.getId())) {
            throw new IllegalArgumentException("过滤规则名称已存在");
        }
        entity.setId(id);
        if (ruleMapper.updateRule(entity) == 0) {
            throw new IllegalArgumentException("过滤规则不存在");
        }
        replaceKeywords(id, entity.getKeywords());
        return toResponse(requireRule(id));
    }

    @Transactional
    public void delete(Long id) {
        requireRule(id);
        ruleMapper.deleteKeywords(id);
        if (ruleMapper.deleteRule(id) == 0) {
            throw new IllegalArgumentException("过滤规则不存在");
        }
    }

    private SystemAlertRuleEntity requireRule(Long id) {
        SystemAlertRuleEntity entity = ruleMapper.findById(id);
        if (entity == null) {
            throw new IllegalArgumentException("过滤规则不存在");
        }
        return attachKeywords(List.of(entity)).getFirst();
    }

    private SystemAlertRuleEntity toEntity(SystemAlertRuleEntity entity, SystemAlertRuleSaveRequest request) {
        entity.setRuleName(requireValue(request.getRuleName(), "规则名称不能为空", 128));
        entity.setAction(parseEnum(SystemAlertRuleAction.class, request.getAction(), "处理动作非法").name());
        entity.setMatchScope(parseEnum(SystemAlertRuleMatchScope.class, request.getMatchScope(), "匹配范围非法").name());
        entity.setMatchMode(parseEnum(SystemAlertRuleMatchMode.class, request.getMatchMode(), "匹配方式非法").name());
        entity.setKeywords(normalizeKeywords(request.getKeywords()));
        int priority = request.getPriority() == null ? 100 : request.getPriority();
        if (priority < 0 || priority > 100_000) {
            throw new IllegalArgumentException("优先级必须在0到100000之间");
        }
        entity.setPriority(priority);
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        String remark = trimToNull(request.getRemark());
        if (remark != null && remark.length() > 500) {
            throw new IllegalArgumentException("备注最多500个字符");
        }
        entity.setRemark(remark);
        return entity;
    }

    private List<String> normalizeKeywords(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("至少配置一个匹配关键词");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            normalized.add(requireValue(value, "匹配关键词不能为空", 255));
        }
        if (normalized.size() > 10) {
            throw new IllegalArgumentException("匹配关键词最多配置10个");
        }
        return List.copyOf(normalized);
    }

    private List<SystemAlertRuleEntity> attachKeywords(List<SystemAlertRuleEntity> rules) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rules.stream().map(SystemAlertRuleEntity::getId).toList();
        Map<Long, List<String>> keywordsByRule = ruleMapper.findKeywords(ids).stream()
                .collect(Collectors.groupingBy(
                        SystemAlertRuleKeywordEntity::ruleId,
                        Collectors.mapping(SystemAlertRuleKeywordEntity::keyword, Collectors.toList())
                ));
        for (SystemAlertRuleEntity rule : rules) {
            rule.setKeywords(keywordsByRule.getOrDefault(rule.getId(), List.of()));
        }
        return rules;
    }

    private void replaceKeywords(Long ruleId, List<String> keywords) {
        ruleMapper.deleteKeywords(ruleId);
        for (int index = 0; index < keywords.size(); index++) {
            ruleMapper.insertKeyword(ruleId, keywords.get(index), index);
        }
    }

    private SystemAlertRuleResponse toResponse(SystemAlertRuleEntity entity) {
        return new SystemAlertRuleResponse(
                entity.getId(), entity.getRuleName(), entity.getAction(), entity.getMatchScope(),
                entity.getMatchMode(), entity.getKeywords(), entity.getPriority(), entity.getEnabled(),
                entity.getRemark(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private <T extends Enum<T>> T parseEnum(Class<T> type, String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        try {
            return Enum.valueOf(type, normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(message);
        }
    }

    private String requireValue(String value, String message, int maxLength) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(message.replace("不能为空", "最多" + maxLength + "个字符"));
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
