package cn.aslight.workhub.dao.autonomy;

import cn.aslight.workhub.model.autonomy.*;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AutonomyMapper {
    String OBSERVATION_COLUMNS = "id,run_id,agent_id,tool_key,tool_code,source_type,business_line_code,environment_code,payload_json,content_hash,observed_at";
    String CASE_COLUMNS = "id,fingerprint,business_line_code,environment_code,source_type,subject_key,category,title,status,active_run_id,latest_proposal_id,occurrence_count,evidence_version,version,first_seen_at,last_seen_at,last_observed_at,resolved_at,created_at,updated_at";

    @Select("SELECT " + OBSERVATION_COLUMNS + " FROM pm_agent_observation WHERE id=#{id}")
    AgentObservationEntity observation(String id);
    @Select("SELECT " + OBSERVATION_COLUMNS + " FROM pm_agent_observation WHERE run_id=#{runId} AND tool_key=#{key}")
    AgentObservationEntity observationByCall(@Param("runId") String runId, @Param("key") String key);
    @Insert("""
            INSERT INTO pm_agent_observation(id,run_id,agent_id,tool_key,tool_code,source_type,business_line_code,environment_code,payload_json,content_hash,observed_at)
            VALUES(#{id},#{runId},#{agentId},#{toolKey},#{toolCode},#{sourceType},#{businessLineCode},#{environmentCode},#{payloadJson},#{contentHash},#{observedAt})
            """)
    int insertObservation(AgentObservationEntity observation);
    @Select("SELECT id FROM pm_agent_dispatch_lock WHERE id='AUTONOMY_SIGNAL' FOR UPDATE")
    String lockSignals();
    @Select("SELECT " + CASE_COLUMNS + " FROM pm_autonomy_case WHERE fingerprint=#{fingerprint}")
    AutonomyCaseEntity byFingerprint(String fingerprint);
    @Select("SELECT " + CASE_COLUMNS + " FROM pm_autonomy_case WHERE id=#{id}")
    AutonomyCaseEntity find(String id);
    @Select("SELECT " + CASE_COLUMNS + " FROM pm_autonomy_case WHERE id=#{id} FOR UPDATE")
    AutonomyCaseEntity lockCase(String id);
    @Select("SELECT COUNT(*) FROM pm_autonomy_case WHERE active_run_id=#{runId} AND status NOT IN ('RESOLVED','CANCELLED')")
    long activeCaseCount(String runId);
    String SOURCE_FILTER = " AND source_type IN <foreach collection='sources' item='source' open='(' separator=',' close=')'>#{source}</foreach>";
    @Select("<script>SELECT " + CASE_COLUMNS + " FROM pm_autonomy_case WHERE business_line_code=#{line} AND environment_code=#{environment}" + SOURCE_FILTER + " ORDER BY updated_at DESC,id LIMIT #{limit} OFFSET #{offset}</script>")
    List<AutonomyCaseEntity> list(@Param("line") String line, @Param("environment") String environment, @Param("sources") List<String> sources, @Param("offset") int offset, @Param("limit") int limit);
    @Select("<script>SELECT COUNT(*) FROM pm_autonomy_case WHERE business_line_code=#{line} AND environment_code=#{environment}" + SOURCE_FILTER + "</script>")
    long count(@Param("line") String line, @Param("environment") String environment, @Param("sources") List<String> sources);
    @Insert("""
            INSERT INTO pm_autonomy_case(id,fingerprint,business_line_code,environment_code,source_type,subject_key,category,title,status,active_run_id,latest_proposal_id,occurrence_count,evidence_version,version,first_seen_at,last_seen_at,last_observed_at,resolved_at,created_at,updated_at)
            VALUES(#{id},#{fingerprint},#{businessLineCode},#{environmentCode},#{sourceType},#{subjectKey},#{category},#{title},#{status},#{activeRunId},#{latestProposalId},#{occurrenceCount},#{evidenceVersion},#{version},#{firstSeenAt},#{lastSeenAt},#{lastObservedAt},#{resolvedAt},#{createdAt},#{updatedAt})
            """)
    int insertCase(AutonomyCaseEntity value);
    @Select("SELECT id,signal_key,case_id,observation_id,evidence_pointer,source_event_id,source_version,evidence_json,occurred_at,observed_at FROM pm_autonomy_signal WHERE signal_key=#{key}")
    AutonomySignalEntity signal(String key);
    @Select("SELECT id,signal_key,case_id,observation_id,evidence_pointer,source_event_id,source_version,evidence_json,occurred_at,observed_at FROM pm_autonomy_signal WHERE case_id=#{id} ORDER BY observed_at DESC,id LIMIT #{limit} OFFSET #{offset}")
    List<AutonomySignalEntity> signals(@Param("id") String id, @Param("offset") int offset, @Param("limit") int limit);
    @Select("SELECT COUNT(*) FROM pm_autonomy_signal WHERE case_id=#{id}")
    long countSignals(String id);
    @Insert("""
            INSERT INTO pm_autonomy_signal(id,signal_key,case_id,observation_id,evidence_pointer,source_event_id,source_version,evidence_json,occurred_at,observed_at)
            VALUES(#{id},#{signalKey},#{caseId},#{observationId},#{evidencePointer},#{sourceEventId},#{sourceVersion},#{evidenceJson},#{occurredAt},#{observedAt})
            """)
    int insertSignal(AutonomySignalEntity signal);
    @Update("""
            UPDATE pm_autonomy_case SET occurrence_count=occurrence_count+#{newSignal},evidence_version=evidence_version+#{newSignal},
              first_seen_at=CASE WHEN first_seen_at IS NULL OR first_seen_at>#{occurred} THEN #{occurred} ELSE first_seen_at END,
              last_seen_at=CASE WHEN last_seen_at IS NULL OR last_seen_at<#{occurred} THEN #{occurred} ELSE last_seen_at END,
              last_observed_at=CASE WHEN last_observed_at<#{observed} THEN #{observed} ELSE last_observed_at END,
              status=CASE WHEN #{reopen}=1 THEN 'DISCOVERED' WHEN #{reopen}=2 THEN 'NEEDS_INPUT' ELSE status END,
              active_run_id=CASE WHEN #{reopen}>0 THEN NULL ELSE active_run_id END,
              latest_proposal_id=CASE WHEN #{reopen}>0 THEN NULL ELSE latest_proposal_id END,
              resolved_at=CASE WHEN #{reopen}>0 THEN NULL ELSE resolved_at END,
              version=version+CASE WHEN #{reopen}>0 THEN 1 ELSE 0 END,updated_at=#{now} WHERE id=#{id}
            """)
    int observe(@Param("id") String id, @Param("newSignal") int newSignal, @Param("occurred") LocalDateTime occurred,
                @Param("observed") LocalDateTime observed, @Param("reopen") int reopen, @Param("now") LocalDateTime now);
    @Update("UPDATE pm_autonomy_case SET active_run_id=#{runId},status='ANALYZING',version=version+1,updated_at=#{now} WHERE id=#{id} AND version=#{version}")
    int claim(@Param("id") String id, @Param("runId") String runId, @Param("version") long version, @Param("now") LocalDateTime now);
    @Select("SELECT id,case_id,run_id,request_key,request_hash,case_version,proposal_json,created_at FROM pm_autonomy_proposal WHERE case_id=#{id} AND run_id=#{runId} AND request_key=#{key}")
    AutonomyProposalEntity proposalByKey(@Param("id") String id, @Param("runId") String runId, @Param("key") String key);
    @Select("SELECT id,case_id,run_id,request_key,request_hash,case_version,proposal_json,created_at FROM pm_autonomy_proposal WHERE id=#{id}")
    AutonomyProposalEntity proposal(String id);
    @Select("SELECT id,case_id,run_id,request_key,request_hash,case_version,proposal_json,created_at FROM pm_autonomy_proposal WHERE case_id=#{id} ORDER BY created_at DESC,id LIMIT #{limit} OFFSET #{offset}")
    List<AutonomyProposalEntity> proposals(@Param("id") String id, @Param("offset") int offset, @Param("limit") int limit);
    @Select("SELECT COUNT(*) FROM pm_autonomy_proposal WHERE case_id=#{id}")
    long countProposals(String id);
    @Insert("""
            INSERT INTO pm_autonomy_proposal(id,case_id,run_id,request_key,request_hash,case_version,proposal_json,created_at)
            VALUES(#{id},#{caseId},#{runId},#{requestKey},#{requestHash},#{caseVersion},#{proposalJson},#{createdAt})
            """)
    int insertProposal(AutonomyProposalEntity proposal);
    @Update("UPDATE pm_autonomy_case SET latest_proposal_id=#{proposalId},version=version+1,updated_at=#{now} WHERE id=#{id} AND version=#{version} AND active_run_id=#{runId}")
    int setProposal(@Param("id") String id, @Param("runId") String runId, @Param("proposalId") String proposalId, @Param("version") long version, @Param("now") LocalDateTime now);
    @Select("SELECT COUNT(*) FROM pm_autonomy_case_run WHERE case_id=#{caseId} AND run_id=#{runId}")
    int linked(@Param("caseId") String caseId, @Param("runId") String runId);
    @Insert("INSERT INTO pm_autonomy_case_run(case_id,run_id,relation,created_at) VALUES(#{caseId},#{runId},#{relation},#{now})")
    int link(@Param("caseId") String caseId, @Param("runId") String runId, @Param("relation") String relation, @Param("now") LocalDateTime now);
    @Update("UPDATE pm_autonomy_case_run SET relation='ANALYSIS' WHERE case_id=#{caseId} AND run_id=#{runId}")
    int linkAnalysis(@Param("caseId") String caseId, @Param("runId") String runId);
    @Select("SELECT case_id,run_id,relation,created_at FROM pm_autonomy_case_run WHERE case_id=#{id} ORDER BY created_at DESC,run_id LIMIT 20")
    List<AutonomyCaseRunEntity> relatedRuns(String id);
    @Select("SELECT COUNT(*) FROM pm_autonomy_case_run WHERE case_id=#{id}")
    long countRuns(String id);

    String BUG_LINK_COLUMNS = "id,case_id,work_item_id,work_item_version,snapshot_json,observation_id,evidence_pointer,signal_id,reason,run_id,case_version,created_at";
    @Select("SELECT " + BUG_LINK_COLUMNS + " FROM pm_autonomy_bug_link WHERE id=#{id}")
    AutonomyBugLinkEntity bugLink(String id);
    @Select("SELECT " + BUG_LINK_COLUMNS + " FROM pm_autonomy_bug_link WHERE case_id=#{caseId} AND work_item_id=#{workItemId} ORDER BY case_version DESC LIMIT 1")
    AutonomyBugLinkEntity latestBugLink(@Param("caseId") String caseId, @Param("workItemId") long workItemId);
    @Select("SELECT " + BUG_LINK_COLUMNS + " FROM pm_autonomy_bug_link b WHERE case_id=#{caseId} AND NOT EXISTS (SELECT 1 FROM pm_autonomy_bug_link newer WHERE newer.case_id=b.case_id AND newer.work_item_id=b.work_item_id AND newer.case_version>b.case_version) ORDER BY work_item_id")
    List<AutonomyBugLinkEntity> currentBugLinks(String caseId);
    @Select("SELECT " + BUG_LINK_COLUMNS + " FROM pm_autonomy_bug_link WHERE case_id=#{caseId} ORDER BY case_version DESC LIMIT #{limit} OFFSET #{offset}")
    List<AutonomyBugLinkEntity> bugLinks(@Param("caseId") String caseId, @Param("offset") int offset, @Param("limit") int limit);
    @Select("SELECT COUNT(*) FROM pm_autonomy_bug_link WHERE case_id=#{caseId}")
    long countBugLinks(String caseId);
    @Insert("""
            INSERT INTO pm_autonomy_bug_link(id,case_id,work_item_id,work_item_version,snapshot_json,observation_id,evidence_pointer,signal_id,reason,run_id,case_version,created_at)
            VALUES(#{id},#{caseId},#{workItemId},#{workItemVersion},#{snapshotJson},#{observationId},#{evidencePointer},#{signalId},#{reason},#{runId},#{caseVersion},#{createdAt})
            """)
    int insertBugLink(AutonomyBugLinkEntity link);
    @Select("SELECT case_id,run_id,request_key,request_hash,link_id,created_at FROM pm_autonomy_bug_link_request WHERE case_id=#{caseId} AND run_id=#{runId} AND request_key=#{key}")
    AutonomyBugLinkRequest bugLinkRequest(@Param("caseId") String caseId, @Param("runId") String runId, @Param("key") String key);
    @Insert("INSERT INTO pm_autonomy_bug_link_request(case_id,run_id,request_key,request_hash,link_id,created_at) VALUES(#{caseId},#{runId},#{requestKey},#{requestHash},#{linkId},#{createdAt})")
    int insertBugLinkRequest(AutonomyBugLinkRequest request);
    @Update("UPDATE pm_autonomy_case SET latest_proposal_id=NULL,version=version+1,updated_at=#{now} WHERE id=#{id} AND version=#{version}")
    int reviseBugContext(@Param("id") String id, @Param("version") long version, @Param("now") LocalDateTime now);

    @Select("SELECT * FROM pm_autonomy_case_resolution WHERE verification_id=#{id}")
    AutonomyCaseResolution resolutionForVerification(String id);
    @Select("SELECT * FROM pm_autonomy_case_resolution WHERE case_id=#{id} AND case_version=#{version}")
    AutonomyCaseResolution resolutionForVersion(@Param("id") String id,@Param("version") long version);
    @Select("SELECT * FROM pm_autonomy_case_resolution WHERE case_id=#{id} ORDER BY case_version DESC LIMIT #{limit} OFFSET #{offset}")
    List<AutonomyCaseResolution> resolutions(@Param("id") String id,@Param("offset") int offset,@Param("limit") int limit);
    @Select("SELECT COUNT(*) FROM pm_autonomy_case_resolution WHERE case_id=#{id}")
    long countResolutions(String id);
    @Insert("""
            INSERT INTO pm_autonomy_case_resolution(id,case_id,case_version,resolved_version,evidence_version,run_id,
              verification_id,plan_id,change_set_id,manifest_hash,proposal_id,proposal_hash,artifact_id,artifact_hash,
              check_ids_json,window_start,window_end,reason,created_at)
            VALUES(#{id},#{caseId},#{caseVersion},#{resolvedVersion},#{evidenceVersion},#{runId},#{verificationId},#{planId},
              #{changeSetId},#{manifestHash},#{proposalId},#{proposalHash},#{artifactId},#{artifactHash},#{checkIdsJson},
              #{windowStart},#{windowEnd},#{reason},#{createdAt})
            """)
    int insertResolution(AutonomyCaseResolution value);
    @Update("""
            UPDATE pm_autonomy_case SET status='RESOLVED',active_run_id=NULL,resolved_at=#{now},version=version+1,updated_at=#{now}
            WHERE id=#{id} AND version=#{version} AND evidence_version=#{evidenceVersion} AND active_run_id=#{runId}
              AND status='ANALYZING'
            """)
    int resolve(@Param("id") String id,@Param("runId") String runId,@Param("version") long version,
            @Param("evidenceVersion") long evidenceVersion,@Param("now") LocalDateTime now);
    @Select("""
            SELECT COUNT(*) FROM pm_agent_verification
            WHERE id=#{verificationId} AND status='PASSED' AND run_id=#{runId} AND plan_id=#{planId}
              AND change_set_id=#{changeSetId} AND manifest_hash=#{manifestHash} AND artifact_id=#{artifactId} AND artifact_hash=#{artifactHash}
            """)
    int resolutionProofMatches(AutonomyCaseResolution value);

}
