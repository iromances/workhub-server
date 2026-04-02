package cn.aslight.workhub.dao.workitem;

import cn.aslight.workhub.model.workitem.WorkItemFollowUpResponse;
import cn.aslight.workhub.model.workitem.WorkItemFollowUpEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 工作项跟踪记录数据访问接口。
 */
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
