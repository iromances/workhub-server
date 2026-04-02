package cn.aslight.workhub.model.intake;

/**
 * IntakeAIDraft 模型。
 */
public record IntakeAIDraft(String titleSuggestion,
                            String descriptionSuggestion,
                            String typeSuggestion,
                            String prioritySuggestion,
                            String suggestedProjectCode,
                            String acceptanceCriteriaSuggestion,
                            java.util.List<IntakeTaskBreakdownItem> taskBreakdownSuggestions,
                            String provider,
                            String model,
                            String rawResponse) {
}
