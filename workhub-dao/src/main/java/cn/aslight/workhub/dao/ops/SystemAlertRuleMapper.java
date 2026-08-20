package cn.aslight.workhub.dao.ops;

import cn.aslight.workhub.model.ops.SystemAlertRuleEntity;
import cn.aslight.workhub.model.ops.SystemAlertRuleKeywordEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SystemAlertRuleMapper {

    @Select("""
            <script>
            SELECT r.id, r.rule_name AS ruleName, r.action, r.match_scope AS matchScope,
                   r.match_mode AS matchMode, r.priority, r.enabled, r.remark,
                   r.created_at AS createdAt, r.updated_at AS updatedAt
            FROM ops_system_alert_rule r
            WHERE 1 = 1
            <if test="enabledOnly">
              AND r.enabled = 1
            </if>
            <if test="keyword != null and keyword != ''">
              AND (
                r.rule_name LIKE CONCAT('%', #{keyword}, '%')
                OR r.remark LIKE CONCAT('%', #{keyword}, '%')
                OR EXISTS (
                  SELECT 1 FROM ops_system_alert_rule_keyword k
                  WHERE k.rule_id = r.id AND k.keyword LIKE CONCAT('%', #{keyword}, '%')
                )
              )
            </if>
            ORDER BY r.priority ASC, r.id ASC
            </script>
            """)
    List<SystemAlertRuleEntity> findRules(@Param("enabledOnly") boolean enabledOnly,
                                          @Param("keyword") String keyword);

    @Select("""
            SELECT id, rule_name AS ruleName, action, match_scope AS matchScope,
                   match_mode AS matchMode, priority, enabled, remark,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM ops_system_alert_rule WHERE id = #{id}
            """)
    SystemAlertRuleEntity findById(Long id);

    @Select("SELECT id, rule_name AS ruleName FROM ops_system_alert_rule WHERE rule_name = #{ruleName}")
    SystemAlertRuleEntity findByName(String ruleName);

    @Select({
            "<script>",
            "SELECT id, rule_id AS ruleId, keyword, sort_order AS sortOrder",
            "FROM ops_system_alert_rule_keyword",
            "WHERE rule_id IN",
            "<foreach collection='ruleIds' item='ruleId' open='(' separator=',' close=')'>",
            "#{ruleId}",
            "</foreach>",
            "ORDER BY rule_id ASC, sort_order ASC, id ASC",
            "</script>"
    })
    List<SystemAlertRuleKeywordEntity> findKeywords(@Param("ruleIds") List<Long> ruleIds);

    @Insert("""
            INSERT INTO ops_system_alert_rule (
              rule_name, action, match_scope, match_mode, priority, enabled, remark
            ) VALUES (
              #{ruleName}, #{action}, #{matchScope}, #{matchMode}, #{priority}, #{enabled}, #{remark}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRule(SystemAlertRuleEntity entity);

    @Update("""
            UPDATE ops_system_alert_rule
            SET rule_name = #{ruleName}, action = #{action}, match_scope = #{matchScope},
                match_mode = #{matchMode}, priority = #{priority}, enabled = #{enabled}, remark = #{remark}
            WHERE id = #{id}
            """)
    int updateRule(SystemAlertRuleEntity entity);

    @Insert("""
            INSERT INTO ops_system_alert_rule_keyword (rule_id, keyword, sort_order)
            VALUES (#{ruleId}, #{keyword}, #{sortOrder})
            """)
    int insertKeyword(@Param("ruleId") Long ruleId,
                      @Param("keyword") String keyword,
                      @Param("sortOrder") int sortOrder);

    @Delete("DELETE FROM ops_system_alert_rule_keyword WHERE rule_id = #{ruleId}")
    int deleteKeywords(Long ruleId);

    @Delete("DELETE FROM ops_system_alert_rule WHERE id = #{id}")
    int deleteRule(Long id);
}
