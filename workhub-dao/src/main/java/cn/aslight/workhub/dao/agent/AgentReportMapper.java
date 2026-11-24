package cn.aslight.workhub.dao.agent;

import org.apache.ibatis.annotations.*;
import cn.aslight.workhub.model.agent.AgentReportRows.*;
import java.util.List;

@Mapper
public interface AgentReportMapper {
    @Update("UPDATE pm_agent_report_finding SET issue_id=#{issueId} WHERE signature=#{signature} AND report_id IN (SELECT id FROM pm_agent_report WHERE run_id=#{runId})")
    int linkIssue(@Param("runId") String runId, @Param("signature") String signature, @Param("issueId") String issueId);
    @Insert("INSERT INTO pm_agent_report (`id`,`run_id`,`task_id`,`agent_id`,`schema_version`,`report_type`,`title`,`conclusion`,`assessment`,`scope_json`,`limitations_json`,`original_event_id`,`payload_hash`,`created_at`) VALUES (#{id},#{runId},#{taskId},#{agentId},#{schemaVersion},#{reportType},#{title},#{conclusion},#{assessment},#{scopeJson},#{limitationsJson},#{originalEventId},#{payloadHash},#{createdAt})")
    int insertReport(Report row);
    @Select("SELECT `id` AS `id`,`run_id` AS `runId`,`task_id` AS `taskId`,`agent_id` AS `agentId`,`schema_version` AS `schemaVersion`,`report_type` AS `reportType`,`title` AS `title`,`conclusion` AS `conclusion`,`assessment` AS `assessment`,`scope_json` AS `scopeJson`,`limitations_json` AS `limitationsJson`,`original_event_id` AS `originalEventId`,`payload_hash` AS `payloadHash`,`created_at` AS `createdAt` FROM pm_agent_report WHERE run_id=#{runId}")
    Report find(String runId);
    @Delete("DELETE FROM pm_agent_report WHERE run_id=#{id}")
    int deleteReportByRun(String id);
    @Delete("DELETE FROM pm_agent_report WHERE task_id=#{id}")
    int deleteReportByTask(String id);
    @Delete("DELETE FROM pm_agent_report WHERE agent_id=#{id}")
    int deleteReportByAgent(String id);
    @Insert("INSERT INTO pm_agent_report_check (`id`,`report_id`,`item_key`,`ordinal`,`category`,`title`,`outcome`,`result_text`) VALUES (#{id},#{reportId},#{key},#{ordinal},#{category},#{title},#{outcome},#{result})")
    int insertCheck(Check row);
    @Select("SELECT `id` AS `id`,`report_id` AS `reportId`,`item_key` AS `key`,`ordinal` AS `ordinal`,`category` AS `category`,`title` AS `title`,`outcome` AS `outcome`,`result_text` AS `result` FROM pm_agent_report_check WHERE report_id=#{reportId} ORDER BY ordinal")
    List<Check> listCheck(String reportId);
    @Delete("DELETE FROM pm_agent_report_check WHERE report_id IN (SELECT id FROM pm_agent_report WHERE run_id=#{id})")
    int deleteCheckByRun(String id);
    @Delete("DELETE FROM pm_agent_report_check WHERE report_id IN (SELECT id FROM pm_agent_report WHERE task_id=#{id})")
    int deleteCheckByTask(String id);
    @Delete("DELETE FROM pm_agent_report_check WHERE report_id IN (SELECT id FROM pm_agent_report WHERE agent_id=#{id})")
    int deleteCheckByAgent(String id);
    @Insert("INSERT INTO pm_agent_report_finding (`id`,`report_id`,`item_key`,`ordinal`,`title`,`severity`,`verification_status`,`disposition`,`category`,`trigger_text`,`analysis_text`,`impact_text`,`recommendation_text`,`signature`,`domain`,`issue_id`) VALUES (#{id},#{reportId},#{key},#{ordinal},#{title},#{severity},#{verificationStatus},#{disposition},#{category},#{trigger},#{analysis},#{impact},#{recommendation},#{signature},#{domain},#{issueId})")
    int insertFinding(Finding row);
    @Select("SELECT `id` AS `id`,`report_id` AS `reportId`,`item_key` AS `key`,`ordinal` AS `ordinal`,`title` AS `title`,`severity` AS `severity`,`verification_status` AS `verificationStatus`,`disposition` AS `disposition`,`category` AS `category`,`trigger_text` AS `trigger`,`analysis_text` AS `analysis`,`impact_text` AS `impact`,`recommendation_text` AS `recommendation`,`signature` AS `signature`,`domain` AS `domain`,`issue_id` AS `issueId` FROM pm_agent_report_finding WHERE report_id=#{reportId} ORDER BY ordinal")
    List<Finding> listFinding(String reportId);
    @Delete("DELETE FROM pm_agent_report_finding WHERE report_id IN (SELECT id FROM pm_agent_report WHERE run_id=#{id})")
    int deleteFindingByRun(String id);
    @Delete("DELETE FROM pm_agent_report_finding WHERE report_id IN (SELECT id FROM pm_agent_report WHERE task_id=#{id})")
    int deleteFindingByTask(String id);
    @Delete("DELETE FROM pm_agent_report_finding WHERE report_id IN (SELECT id FROM pm_agent_report WHERE agent_id=#{id})")
    int deleteFindingByAgent(String id);
    @Insert("INSERT INTO pm_agent_report_evidence (`id`,`report_id`,`item_key`,`ordinal`,`target_type`,`target_key`,`evidence_type`,`source`,`locator`,`observed_at`,`environment_code`,`verification_method`,`result_text`,`limitations_text`) VALUES (#{id},#{reportId},#{key},#{ordinal},#{targetType},#{targetKey},#{type},#{source},#{locator},#{observedAt},#{environment},#{verificationMethod},#{result},#{limitations})")
    int insertEvidence(Evidence row);
    @Select("SELECT `id` AS `id`,`report_id` AS `reportId`,`item_key` AS `key`,`ordinal` AS `ordinal`,`target_type` AS `targetType`,`target_key` AS `targetKey`,`evidence_type` AS `type`,`source` AS `source`,`locator` AS `locator`,`observed_at` AS `observedAt`,`environment_code` AS `environment`,`verification_method` AS `verificationMethod`,`result_text` AS `result`,`limitations_text` AS `limitations` FROM pm_agent_report_evidence WHERE report_id=#{reportId} ORDER BY ordinal")
    List<Evidence> listEvidence(String reportId);
    @Delete("DELETE FROM pm_agent_report_evidence WHERE report_id IN (SELECT id FROM pm_agent_report WHERE run_id=#{id})")
    int deleteEvidenceByRun(String id);
    @Delete("DELETE FROM pm_agent_report_evidence WHERE report_id IN (SELECT id FROM pm_agent_report WHERE task_id=#{id})")
    int deleteEvidenceByTask(String id);
    @Delete("DELETE FROM pm_agent_report_evidence WHERE report_id IN (SELECT id FROM pm_agent_report WHERE agent_id=#{id})")
    int deleteEvidenceByAgent(String id);
    @Insert("INSERT INTO pm_agent_report_action (`id`,`report_id`,`item_key`,`ordinal`,`finding_key`,`priority`,`description`,`acceptance_criteria`,`prerequisite_text`) VALUES (#{id},#{reportId},#{key},#{ordinal},#{findingKey},#{priority},#{description},#{acceptanceCriteria},#{prerequisite})")
    int insertAction(Action row);
    @Select("SELECT `id` AS `id`,`report_id` AS `reportId`,`item_key` AS `key`,`ordinal` AS `ordinal`,`finding_key` AS `findingKey`,`priority` AS `priority`,`description` AS `description`,`acceptance_criteria` AS `acceptanceCriteria`,`prerequisite_text` AS `prerequisite` FROM pm_agent_report_action WHERE report_id=#{reportId} ORDER BY ordinal")
    List<Action> listAction(String reportId);
    @Delete("DELETE FROM pm_agent_report_action WHERE report_id IN (SELECT id FROM pm_agent_report WHERE run_id=#{id})")
    int deleteActionByRun(String id);
    @Delete("DELETE FROM pm_agent_report_action WHERE report_id IN (SELECT id FROM pm_agent_report WHERE task_id=#{id})")
    int deleteActionByTask(String id);
    @Delete("DELETE FROM pm_agent_report_action WHERE report_id IN (SELECT id FROM pm_agent_report WHERE agent_id=#{id})")
    int deleteActionByAgent(String id);
}
