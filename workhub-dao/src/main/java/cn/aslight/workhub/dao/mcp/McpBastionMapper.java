package cn.aslight.workhub.dao.mcp;

import cn.aslight.workhub.model.mcp.McpBastionEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface McpBastionMapper {

    @Select("""
            <script>
            SELECT id,
                   name,
                   host,
                   port,
                   username,
                   password_encrypted AS passwordEncrypted,
                   identity_file AS identityFile,
                   enabled,
                   remark,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM mcp_bastion_config
            WHERE 1 = 1
            <if test="keyword != null and keyword != ''">
              AND (
                name LIKE CONCAT('%', #{keyword}, '%')
                OR host LIKE CONCAT('%', #{keyword}, '%')
                OR username LIKE CONCAT('%', #{keyword}, '%')
              )
            </if>
            <if test="enabledOnly">
              AND enabled = 1
            </if>
            ORDER BY enabled DESC, name ASC, id ASC
            </script>
            """)
    List<McpBastionEntity> findAll(@Param("keyword") String keyword,
                                    @Param("enabledOnly") boolean enabledOnly);

    @Select("""
            SELECT id,
                   name,
                   host,
                   port,
                   username,
                   password_encrypted AS passwordEncrypted,
                   identity_file AS identityFile,
                   enabled,
                   remark,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM mcp_bastion_config
            WHERE id = #{id}
            """)
    McpBastionEntity findById(@Param("id") Long id);

    @Select("""
            SELECT id,
                   name,
                   host,
                   port,
                   username,
                   password_encrypted AS passwordEncrypted,
                   identity_file AS identityFile,
                   enabled,
                   remark,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM mcp_bastion_config
            WHERE name = #{name}
            LIMIT 1
            """)
    McpBastionEntity findByName(@Param("name") String name);

    @Insert("""
            INSERT INTO mcp_bastion_config (
                name,
                host,
                port,
                username,
                password_encrypted,
                identity_file,
                enabled,
                remark
            ) VALUES (
                #{name},
                #{host},
                #{port},
                #{username},
                #{passwordEncrypted},
                #{identityFile},
                #{enabled},
                #{remark}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(McpBastionEntity entity);

    @Update("""
            UPDATE mcp_bastion_config
            SET name = #{name},
                host = #{host},
                port = #{port},
                username = #{username},
                password_encrypted = #{passwordEncrypted},
                identity_file = #{identityFile},
                enabled = #{enabled},
                remark = #{remark},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int update(McpBastionEntity entity);

    @Select("""
            SELECT COUNT(1)
            FROM mcp_resource_config
            WHERE resource_type = 'DATABASE'
              AND ssh_bastion_enabled = 1
              AND bastion_id = #{bastionId}
            """)
    int countDatabaseReferences(@Param("bastionId") Long bastionId);
}
