package cn.aslight.workhub.domain.intake.dto;

public record IntakeTaskBreakdownItem(String taskName,
                                      String estimatedEffort,
                                      String ownerUserName,
                                      String status,
                                      String notes) {
}
