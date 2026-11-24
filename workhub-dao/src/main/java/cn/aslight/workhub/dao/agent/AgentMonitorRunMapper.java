package cn.aslight.workhub.dao.agent;

import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface AgentMonitorRunMapper {
    @Select("SELECT monitor_run_id FROM pm_agent_monitor_run WHERE agent_run_id=#{runId} AND config_id=#{configId}")
    Long find(@Param("runId") String runId, @Param("configId") Long configId);

    @Insert("INSERT INTO pm_agent_monitor_run(agent_run_id,config_id,monitor_run_id) VALUES(#{runId},#{configId},#{monitorRunId})")
    void insert(@Param("runId") String runId, @Param("configId") Long configId, @Param("monitorRunId") Long monitorRunId);

    @Select("SELECT monitor_run_id FROM pm_agent_monitor_run WHERE agent_run_id=#{runId} ORDER BY config_id")
    List<Long> runs(String runId);

    @Select("SELECT agent_run_id FROM pm_agent_monitor_run WHERE monitor_run_id=#{monitorRunId} ORDER BY created_at")
    List<String> agents(Long monitorRunId);
}
