package cn.aslight.workhub.dao.intake;

import cn.aslight.workhub.model.intake.IntakeTestDeployment.Row;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** 测试发布尝试独立持久化，活动 Job 唯一键防止并发重复发布。 */
@Mapper
public interface IntakeTestDeploymentMapper {
    @Insert("""
        INSERT INTO pm_intake_test_deployment(id,intake_id,active_key,plan_json,status,requested_by)
        VALUES(#{id},#{intakeId},#{activeKey},#{planJson},'PENDING',#{requestedBy})
        """)
    int insert(Row row);
    @Select("SELECT * FROM pm_intake_test_deployment WHERE id=#{id}")
    Row find(String id);
    @Select("""
        SELECT * FROM pm_intake_test_deployment WHERE intake_id=#{intakeId}
        ORDER BY created_at DESC, id DESC LIMIT 20
        """)
    List<Row> history(Long intakeId);
    @Select("""
        SELECT * FROM pm_intake_test_deployment WHERE status IN ('PENDING','QUEUED','RUNNING')
        ORDER BY created_at LIMIT 30
        """)
    List<Row> pending();
    @Update("""
        UPDATE pm_intake_test_deployment SET status='PREPARING',updated_at=CURRENT_TIMESTAMP
        WHERE id=#{id} AND status='PENDING'
        """)
    int claim(String id);
    @Update("""
        UPDATE pm_intake_test_deployment SET plan_json=#{planJson},status=#{status},queue_id=#{queueId},
        build_number=#{buildNumber},build_url=#{buildUrl},actual_sha=#{actualSha},message=#{message},
        active_key=#{activeKey},updated_at=CURRENT_TIMESTAMP WHERE id=#{id}
        """)
    int update(Row row);
    @Update("""
        UPDATE pm_intake_submit_test_review SET jenkins_environment_code='TEST',
        jenkins_build_number=#{buildNumber},jenkins_build_url=#{buildUrl},jenkins_build_status=#{status},
        updated_at=CURRENT_TIMESTAMP WHERE intake_id=#{intakeId}
        """)
    int updateReview(Row row);
    @Update("""
        UPDATE pm_intake_test_deployment SET status='UNKNOWN',
        message='发布准备或提交被中断，请人工核对；未自动重发',updated_at=CURRENT_TIMESTAMP
        WHERE status IN ('PREPARING','SUBMITTING') AND updated_at < DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 10 MINUTE)
        """)
    int markInterrupted();
}
