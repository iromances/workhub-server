package cn.aslight.workhub.model.intake;

import java.time.LocalDate;

/**
 * 需求列表查询条件，使用主表独立字段筛选。
 */
public record IntakeListQuery(String status,
                              String requirementName,
                              String approvalCode,
                              String proposerName,
                              String businessLine,
                              String requirementType,
                              String demandStatus,
                              LocalDate releasedStartDate,
                              LocalDate releasedEndDate,
                              long offset,
                              int pageSize,
                              String keyword) {
    public IntakeListQuery(String status, String requirementName, String approvalCode, String proposerName,
                           String businessLine, String requirementType, String demandStatus,
                           LocalDate releasedStartDate, LocalDate releasedEndDate, long offset, int pageSize) {
        this(status, requirementName, approvalCode, proposerName, businessLine, requirementType, demandStatus,
                releasedStartDate, releasedEndDate, offset, pageSize, null);
    }
}
