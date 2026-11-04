package cn.aslight.workhub.dao.ops;

import cn.aslight.workhub.model.ops.AccountingExecutionEvidenceEntity;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AccountingExecutionEvidenceMapper {
    @Insert("""
            INSERT INTO ops_account_execution_evidence
              (run_id,business_line_code,environment_code,system_name,display_name,snapshot_json,content_hash,captured_at)
            SELECT #{runId},#{businessLineCode},#{environmentCode},#{systemName},#{displayName},#{snapshotJson},#{contentHash},#{capturedAt}
            FROM ops_account_reconcile_run WHERE id=#{runId} AND status='RUNNING'
            """)
    int insert(AccountingExecutionEvidenceEntity value);

    @Select("SELECT run_id,business_line_code,environment_code,system_name,display_name,snapshot_json,content_hash,captured_at FROM ops_account_execution_evidence WHERE run_id=#{id}")
    AccountingExecutionEvidenceEntity find(Long id);
}
