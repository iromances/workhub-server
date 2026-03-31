package cn.aslight.workhub.domain.workitem.mapper;

import cn.aslight.workhub.domain.workitem.dto.WorkItemFollowUpResponse;
import cn.aslight.workhub.domain.workitem.model.WorkItemFollowUpEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WorkItemFollowUpMapper {

    @Select("""
            SELECT id,
                   content,
                   operator_user_name AS operatorUserName,
                   created_at AS createdAt
            FROM pm_work_item_follow_up
            WHERE work_item_id = #{workItemId}
            ORDER BY id DESC
            """)
    List<WorkItemFollowUpResponse> findByWorkItemId(Long workItemId);

    @Insert("""
            INSERT INTO pm_work_item_follow_up (
                work_item_id,
                content,
                operator_user_name
            ) VALUES (
                #{workItemId},
                #{content},
                #{operatorUserName}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WorkItemFollowUpEntity entity);
}
