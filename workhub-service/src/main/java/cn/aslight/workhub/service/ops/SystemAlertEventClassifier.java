package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.model.ops.ElkSystemAlertLog;
import cn.aslight.workhub.model.ops.SystemAlertRuleAction;
import cn.aslight.workhub.model.ops.SystemAlertRuleEntity;
import cn.aslight.workhub.model.ops.SystemAlertRuleMatchMode;
import cn.aslight.workhub.model.ops.SystemAlertRuleMatchScope;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

final class SystemAlertEventClassifier {

    private SystemAlertEventClassifier() {
    }

    static SystemAlertRuleAction classify(ElkSystemAlertLog event, List<SystemAlertRuleEntity> rules) {
        if (rules == null || rules.isEmpty()) {
            return SystemAlertRuleAction.SYSTEM_ERROR;
        }
        return rules.stream()
                .sorted(Comparator.comparing(SystemAlertEventClassifier::priority)
                        .thenComparing(SystemAlertEventClassifier::id))
                .filter(rule -> Boolean.TRUE.equals(rule.getEnabled()))
                .filter(rule -> matches(event, rule))
                .map(rule -> enumValue(SystemAlertRuleAction.class, rule.getAction()))
                .filter(action -> action != null)
                .findFirst()
                .orElse(SystemAlertRuleAction.SYSTEM_ERROR);
    }

    private static boolean matches(ElkSystemAlertLog event, SystemAlertRuleEntity rule) {
        if (rule.getKeywords() == null || rule.getKeywords().isEmpty()) {
            return false;
        }
        SystemAlertRuleMatchScope scope = enumValue(SystemAlertRuleMatchScope.class, rule.getMatchScope());
        SystemAlertRuleMatchMode mode = enumValue(SystemAlertRuleMatchMode.class, rule.getMatchMode());
        if (scope == null || mode == null) {
            return false;
        }
        String searchableText = scope == SystemAlertRuleMatchScope.MESSAGE
                ? normalize(event.message())
                : normalize(Stream.of(event.title(), event.message(), event.errorType(), event.stackTrace())
                        .filter(value -> value != null && !value.isBlank())
                        .reduce("", (left, right) -> left + '\n' + right));
        List<String> normalizedKeywords = rule.getKeywords().stream()
                .map(SystemAlertEventClassifier::normalize)
                .filter(keyword -> !keyword.isEmpty())
                .toList();
        if (normalizedKeywords.isEmpty()) {
            return false;
        }
        Stream<String> keywords = normalizedKeywords.stream();
        return mode == SystemAlertRuleMatchMode.ALL
                ? keywords.allMatch(searchableText::contains)
                : keywords.anyMatch(searchableText::contains);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.replace(':', '：')
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
    }

    private static int priority(SystemAlertRuleEntity rule) {
        return rule.getPriority() == null ? 100 : rule.getPriority();
    }

    private static long id(SystemAlertRuleEntity rule) {
        return rule.getId() == null ? Long.MAX_VALUE : rule.getId();
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
