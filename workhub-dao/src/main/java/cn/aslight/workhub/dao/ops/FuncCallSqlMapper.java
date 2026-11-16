package cn.aslight.workhub.dao.ops;

import cn.aslight.workhub.model.ops.FuncCallSqlEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** REQ-9f3c2a84：Function Call 场景 SQL 登记读写。 */
@Mapper
public interface FuncCallSqlMapper {

    String COLUMNS = """
            id, sql_code AS sqlCode, scenario_code AS scenarioCode, branch_order AS branchOrder,
            name, description, sql_template AS sqlTemplate, param_desc AS paramDesc,
            source_module AS sourceModule, enabled, remark, created_at AS createdAt, updated_at AS updatedAt
            """;

    @Select("""
            <script>
            SELECT
            """ + COLUMNS + """
            FROM func_call_sql
            WHERE 1 = 1
            <if test="keyword != null and keyword != ''">
              AND (sql_code LIKE CONCAT('%', #{keyword}, '%')
                OR scenario_code LIKE CONCAT('%', #{keyword}, '%')
                OR name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="enabledOnly">
              AND enabled = 1
            </if>
            ORDER BY scenario_code ASC, branch_order ASC, sql_code ASC
            </script>
            """)
    List<FuncCallSqlEntity> findAll(@Param("keyword") String keyword, @Param("enabledOnly") boolean enabledOnly);

    @Select("SELECT " + COLUMNS + " FROM func_call_sql WHERE sql_code = #{sqlCode}")
    FuncCallSqlEntity findByCode(@Param("sqlCode") String sqlCode);

    @Select("SELECT " + COLUMNS + " FROM func_call_sql WHERE id = #{id}")
    FuncCallSqlEntity findById(@Param("id") Long id);

    @Select("SELECT " + COLUMNS + " FROM func_call_sql WHERE scenario_code = #{scenarioCode} ORDER BY branch_order ASC, sql_code ASC")
    List<FuncCallSqlEntity> findByScenario(@Param("scenarioCode") String scenarioCode);

    @Select("""
            <script>
            SELECT
            """ + COLUMNS + """
            FROM func_call_sql
            WHERE (sql_code = #{code} OR scenario_code = #{code})
            ORDER BY branch_order ASC, sql_code ASC
            </script>
            """)
    List<FuncCallSqlEntity> findByCodeOrScenario(@Param("code") String code);

    @Insert("""
            INSERT INTO func_call_sql (
                sql_code, scenario_code, branch_order, name, description,
                sql_template, param_desc, source_module, enabled, remark
            ) VALUES (
                #{sqlCode}, #{scenarioCode}, #{branchOrder}, #{name}, #{description},
                #{sqlTemplate}, #{paramDesc}, #{sourceModule}, #{enabled}, #{remark}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(FuncCallSqlEntity entity);

    @Update("""
            UPDATE func_call_sql
            SET sql_code = #{sqlCode},
                scenario_code = #{scenarioCode},
                branch_order = #{branchOrder},
                name = #{name},
                description = #{description},
                sql_template = #{sqlTemplate},
                param_desc = #{paramDesc},
                source_module = #{sourceModule},
                enabled = #{enabled},
                remark = #{remark},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int update(FuncCallSqlEntity entity);

    @Update("UPDATE func_call_sql SET enabled = #{enabled}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int updateEnabled(@Param("id") Long id, @Param("enabled") boolean enabled);

    @Select("SELECT COUNT(1) FROM func_call_sql WHERE sql_code = #{sqlCode} AND id <> #{id}")
    long countOtherByCode(@Param("sqlCode") String sqlCode, @Param("id") Long id);
}
