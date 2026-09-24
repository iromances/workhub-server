package cn.aslight.workhub.model.ai;

import java.time.Instant;
import java.util.List;

public record CodexModelCatalogResponse(List<Model> models, Instant fetchedAt, boolean cached) {

    public record Model(String model, String displayName, boolean isDefault,
                        String defaultReasoningEffort, List<ReasoningEffort> supportedReasoningEfforts) {
    }

    public record ReasoningEffort(String reasoningEffort, String description) {
    }
}
