package cn.aslight.workhub.model.intake;

/**
 * 研发需求澄清项。
 */
public record IntakeClarificationItem(Integer index,
                                      String itemType,
                                      String title,
                                      String description,
                                      String evidence,
                                      String status,
                                      String responseText,
                                      String respondedBy,
                                      String respondedAt) {
}
