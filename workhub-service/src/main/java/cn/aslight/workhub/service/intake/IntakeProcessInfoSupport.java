package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.model.intake.IntakeProcessInfoResponse;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 过程信息的字段白名单、日期/工时口径及有效预估读取。 */
final class IntakeProcessInfoSupport {
    record Field(String name, String label, String column, boolean date) { }

    static final List<Field> FIELDS = List.of(
            new Field("plannedDevelopmentStartDate", "预估开发日期", "planned_development_start_date", true),
            new Field("developmentStartedDate", "实际开发开始日期", "development_started_date", true),
            new Field("actualCompletedTime", "实际开发完成日期", "actual_completed_date", true),
            new Field("plannedTestingStartDate", "预估提测日期", "planned_testing_start_date", true),
            new Field("testingStartedDate", "实际提测日期", "testing_started_date", true),
            new Field("actualTestingCompletedDate", "实际测试完成日期", "actual_testing_completed_date", true),
            new Field("plannedReleaseDate", "预估上线日期", "planned_release_date", true),
            new Field("releasedTime", "实际上线日期", "released_date", true),
            new Field("scheduledAcceptanceDate", "预约验收日期", "scheduled_acceptance_date", true),
            new Field("acceptanceTime", "实际验收日期", "acceptance_date", true),
            new Field("actualEffort", "实际开发工时", "actual_effort", false),
            new Field("actualTestingEffort", "实际测试工时", "actual_testing_effort", false),
            new Field("developmentEstimatedEffort", "开发预估工时", null, false),
            new Field("testingEstimatedEffort", "测试预估工时", null, false)
    );
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu/M/d")
            .withResolverStyle(ResolverStyle.STRICT);

    private IntakeProcessInfoSupport() { }

    static Field field(String name) {
        return FIELDS.stream().filter(field -> field.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不允许编辑字段：" + name));
    }

    static Map<String, String> estimates(IntakeRecordEntity entity, ObjectMapper mapper) {
        boolean overridden = entity.getProcessEstimatedEffort() != null;
        String source = overridden ? entity.getProcessEstimatedEffort() : entity.getLatestDevelopmentDraftJson();
        JsonNode json = source == null ? mapper.createObjectNode() : mapper.readTree(source);
        Map<String, String> values = new LinkedHashMap<>();
        values.put("developmentEstimatedEffort", overridden
                ? text(json, "developmentEstimatedEffort")
                : first(text(json, "developmentEstimatedEffort"), entity.getDevelopmentEstimatedEffort()));
        values.put("testingEstimatedEffort", overridden
                ? text(json, "testingEstimatedEffort")
                : first(text(json, "testingEstimatedEffort"), entity.getTestingEstimatedEffort()));
        values.put("totalEstimatedEffort", overridden
                ? text(json, "totalEstimatedEffort")
                : first(text(json, "totalEstimatedEffort"), first(entity.getTotalEstimatedEffort(), entity.getEstimatedEffort())));
        values.replaceAll((key, value) -> EffortUnitNormalizer.normalizeEffort(value));
        return values;
    }

    static IntakeProcessInfoResponse read(IntakeRecordEntity entity, IntakeStructuredData structured, ObjectMapper mapper) {
        JsonNode json = structured == null ? mapper.createObjectNode() : mapper.valueToTree(structured);
        Map<String, String> values = new LinkedHashMap<>();
        for (Field field : FIELDS) {
            if (field.column() != null) {
                String value = text(json, field.name());
                values.put(field.name(), field.date()
                        ? storedDate(value)
                        : EffortUnitNormalizer.normalizeEffort(value));
            }
        }
        values.putAll(estimates(entity, mapper));
        return new IntakeProcessInfoResponse(values, entity.getProcessEstimatedEffort() != null);
    }

    static String normalize(Field field, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (field.date()) {
            try {
                return LocalDate.parse(raw.trim().replace('-', '/'), DATE).toString();
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException(field.label() + "格式不正确，请使用 yyyy-MM-dd");
            }
        }
        String effort = EffortUnitNormalizer.normalizeEffort(raw);
        if (!effort.matches("[0-9]+(?:\\.[0-9]+)?h")) {
            throw new IllegalArgumentException(field.label() + "格式不正确，请输入如 8h 或 1d");
        }
        return effort;
    }

    static void validateDates(Map<String, String> values) {
        ordered(values, "plannedDevelopmentStartDate", "plannedTestingStartDate");
        ordered(values, "plannedTestingStartDate", "plannedReleaseDate");
        ordered(values, "plannedDevelopmentStartDate", "plannedReleaseDate");
        ordered(values, "developmentStartedDate", "actualCompletedTime");
        ordered(values, "testingStartedDate", "actualTestingCompletedDate");
    }

    private static void ordered(Map<String, String> values, String start, String end) {
        String from = values.get(start);
        String to = values.get(end);
        if (from != null && to != null && from.compareTo(to) > 0) {
            throw new IllegalArgumentException(field(end).label() + "不能早于" + field(start).label());
        }
    }

    static String total(String development, String testing) {
        if (development == null && testing == null) {
            return null;
        }
        BigDecimal hours = BigDecimal.ZERO;
        for (String value : new String[]{development, testing}) {
            if (value != null) {
                hours = hours.add(new BigDecimal(value.substring(0, value.length() - 1)));
            }
        }
        return hours.stripTrailingZeros().toPlainString() + "h";
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).isNull() || node.path(field).isMissingNode() ? null : node.path(field).asText();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String storedDate(String value) {
        // 兼容既有业务日期读取口径：旧 JSON 可带时间，或以“无”/“-”表示空值。
        if (value == null || "无".equals(value) || "-".equals(value)) {
            return null;
        }
        return LocalDate.parse(value.split("[T ]", 2)[0].replace('-', '/'), DATE).toString();
    }

    private static String first(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() || "null".equals(preferred) ? fallback : preferred;
    }
}
