package cn.aslight.workhub.model.intake;

import java.util.List;

/**
 * 需求列表数据库分页结果。
 */
public record IntakePageResponse(long total, List<IntakeSummaryResponse> items) {
}
