package cn.aslight.workhub.model.autonomy;

/** latest 指该问题与该缺陷的最新关联；sourceState 另行核对当前工作项，历史不随原文编辑而覆盖。 */
public record AutonomyBugLinkView(AutonomyBugLinkEntity link, boolean latest, String sourceState) { }
