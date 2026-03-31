package cn.aslight.workhub.domain.workitem.mapper;

import cn.aslight.workhub.domain.workitem.dto.WorkItemTransitionResponse;
import cn.aslight.workhub.domain.workitem.model.WorkItemTransitionLogEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WorkItemTransitionLogMapper {

    @Select("""
            SELECT id,
                   from_status AS fromStatus,
                   to_status AS toStatus,
                   reason,
                   operator_user_name AS operatorUserName,
                   created_at AS createdAt
            FROM pm_work_item_transition_log
            WHERE work_item_id = #{workItemId}
            ORDER BY id DESC
            """)
    List<WorkItemTransitionResponse> findByWorkItemId(Long workItemId);

    @Insert("""
            INSERT INTO pm_work_item_transition_log (
                work_item_id,
                from_status,
                to_status,
                reason,
                operator_user_name
            ) VALUES (
                #{workItemId},
                #{fromStatus},
                #{toStatus},
                #{reason},
                #{operatorUserName}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WorkItemTransitionLogEntity entity);
}
