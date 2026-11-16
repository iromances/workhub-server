package cn.aslight.workhub.dao.agent;

import cn.aslight.workhub.model.agent.AgentToolRegistrationEntity;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** 工具展示文案的读取与乐观锁保存；工具目录在代码 AgentToolCatalog，本表行可随目录演进增删。 */
@Mapper
public interface AgentToolMapper {
    String COLUMNS = "code, name, description, version, updated_by, created_at, updated_at";

    @Select("SELECT " + COLUMNS + " FROM pm_agent_tool ORDER BY code")
    List<AgentToolRegistrationEntity> list();

    @Select("SELECT " + COLUMNS + " FROM pm_agent_tool WHERE code=#{code}")
    AgentToolRegistrationEntity find(@Param("code") String code);

    @Insert("""
            INSERT INTO pm_agent_tool(code,name,description,version,updated_by,created_at,updated_at)
            VALUES (#{code},#{name},#{description},0,#{updatedBy},#{createdAt},#{updatedAt})
            """)
    int insert(AgentToolRegistrationEntity entity);

    @Update("""
            UPDATE pm_agent_tool SET name=#{name},description=#{description},version=version+1,updated_by=#{updatedBy},updated_at=#{updatedAt}
            WHERE code=#{code} AND version=#{version}
            """)
    int update(AgentToolRegistrationEntity entity);
}
