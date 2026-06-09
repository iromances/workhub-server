package cn.aslight.workhub.model.intake;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 需求澄清项人工回复请求。
 */
public record IntakeClarificationReplyRequest(@NotBlank String itemType,
                                              @NotNull Integer itemIndex,
                                              String responseText) {
}
