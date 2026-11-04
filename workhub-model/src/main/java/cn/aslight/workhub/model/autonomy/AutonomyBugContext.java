package cn.aslight.workhub.model.autonomy;

/** 核对全部最新缺陷关联的原文状态；不受历史记录分页影响。 */
public record AutonomyBugContext(long historyCount, long bugCount, long changedCount, long unavailableCount) { }
