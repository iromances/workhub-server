package cn.aslight.workhub.model.intake;

/**
 * IntakeTaskBreakdownItem 模型。
 */
public record IntakeTaskBreakdownItem(String taskName,
                                      String estimatedEffort,
                                      String ownerUserName,
                                      String status,
                                      String notes) {
}
