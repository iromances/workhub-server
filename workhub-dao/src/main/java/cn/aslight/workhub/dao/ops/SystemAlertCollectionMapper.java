package cn.aslight.workhub.dao.ops;

import cn.aslight.workhub.model.ops.SystemAlertCollectionState;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SystemAlertCollectionMapper {
    @Insert("""
        INSERT IGNORE INTO ops_system_alert_collection_state(scope_id,window_id,definition_json,definition_hash,from_time,to_time,
          status,issue_count,page_number,version,updated_at)
        VALUES(#{scopeId},#{windowId},#{definitionJson},#{definitionHash},#{fromTime},#{toTime},'ACTIVE',0,0,0,#{updatedAt})
        """) int ensure(SystemAlertCollectionState value);
    @Select("SELECT * FROM ops_system_alert_collection_state WHERE scope_id=#{id} FOR UPDATE")
    SystemAlertCollectionState lock(Long id);
    @Select("SELECT * FROM ops_system_alert_collection_state WHERE scope_id=#{id}")
    SystemAlertCollectionState find(Long id);
    @Select("""
        SELECT c.scope_id FROM ops_system_alert_collection_state c JOIN ops_system_alert_scope s ON s.id=c.scope_id AND s.enabled=1
        WHERE c.status IN ('ACTIVE','ERROR') AND (c.owner_token IS NULL OR c.lease_until<=#{now})
        ORDER BY c.updated_at,c.scope_id LIMIT 100
        """) List<Long> pending(LocalDateTime now);
    @Update("""
        UPDATE ops_system_alert_collection_state SET window_id=#{windowId},definition_json=#{definitionJson},definition_hash=#{definitionHash},
          from_time=#{fromTime},to_time=#{toTime},position_json=#{positionJson},latest_occurred_at=#{latestOccurredAt},covered_until=#{coveredUntil},
          status=#{status},owner_token=#{ownerToken},lease_until=#{leaseUntil},issue_count=#{issueCount},page_number=#{pageNumber},version=version+1,updated_at=#{updatedAt}
        WHERE scope_id=#{scopeId} AND version=#{version}
        """) int update(SystemAlertCollectionState value);
    @Insert("""
        INSERT INTO ops_system_alert_collection_page(id,scope_id,window_id,page_number,definition_hash,from_time,to_time,
          complete,receipt_json,receipt_hash,created_at)
        VALUES(#{id},#{scopeId},#{windowId},#{pageNumber},#{definitionHash},#{fromTime},#{toTime},#{complete},#{receiptJson},#{receiptHash},#{createdAt})
        """) int insertPage(Page value);
    @Select("SELECT * FROM ops_system_alert_collection_page WHERE window_id=#{id} ORDER BY page_number")
    List<Page> pages(String id);
    record Page(String id, Long scopeId, String windowId, long pageNumber, String definitionHash, LocalDateTime fromTime,
        LocalDateTime toTime, boolean complete, String receiptJson, String receiptHash, LocalDateTime createdAt) { }
}
