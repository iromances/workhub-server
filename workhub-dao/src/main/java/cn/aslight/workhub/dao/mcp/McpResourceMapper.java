package cn.aslight.workhub.dao.mcp;

import cn.aslight.workhub.model.mcp.McpResourceEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface McpResourceMapper {

    @Select("""
            <script>
            SELECT id,
                   resource_type AS resourceType,
                   target_key AS targetKey,
                   public_resource AS publicResource,
                   feature_tags_json AS featureTagsJson,
                   business_line_code AS businessLineCode,
                   environment_code AS environmentCode,
                   name,
                   system_name AS systemName,
                   host,
                   port,
                   database_schema AS databaseSchema,
                   username,
                   secret_ref AS secretRef,
                   password_encrypted AS passwordEncrypted,
                   ssh_password_encrypted AS sshPasswordEncrypted,
                   ssh_bastion_enabled AS sshBastionEnabled,
                   bastion_id AS bastionId,
                   ssh_bastion_host AS sshBastionHost,
                   ssh_bastion_port AS sshBastionPort,
                   ssh_bastion_user AS sshBastionUser,
                   ssh_bastion_password_encrypted AS sshBastionPasswordEncrypted,
                   ssh_identity_file AS sshIdentityFile,
                   allowed_services_json AS allowedServicesJson,
                   allowed_log_paths_json AS allowedLogPathsJson,
                   profiles_json AS profilesJson,
                   enabled,
                   remark,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM mcp_resource_config
            WHERE 1 = 1
            <if test="resourceType != null and resourceType != ''">
              AND resource_type = #{resourceType}
            </if>
            <if test="businessLineCode != null and businessLineCode != ''">
              AND (
                public_resource = 1
                OR business_line_code = #{businessLineCode}
                OR EXISTS (
                  SELECT 1
                  FROM mcp_resource_business_line rel
                  WHERE rel.resource_id = mcp_resource_config.id
                    AND rel.business_line_code = #{businessLineCode}
                )
              )
            </if>
            <if test="environmentCode != null and environmentCode != ''">
              AND environment_code = #{environmentCode}
            </if>
            <if test="enabledOnly">
              AND enabled = 1
            </if>
            <if test="keyword != null and keyword != ''">
              AND (
                target_key LIKE CONCAT('%', #{keyword}, '%')
                OR name LIKE CONCAT('%', #{keyword}, '%')
                OR host LIKE CONCAT('%', #{keyword}, '%')
                OR feature_tags_json LIKE CONCAT('%', #{keyword}, '%')
              )
            </if>
            ORDER BY public_resource DESC, business_line_code ASC, environment_code ASC, resource_type ASC, target_key ASC
            </script>
            """)
    List<McpResourceEntity> findAll(@Param("resourceType") String resourceType,
                                    @Param("businessLineCode") String businessLineCode,
                                    @Param("environmentCode") String environmentCode,
                                    @Param("keyword") String keyword,
                                    @Param("enabledOnly") boolean enabledOnly);

    @Select("""
            SELECT id,
                   resource_type AS resourceType,
                   target_key AS targetKey,
                   public_resource AS publicResource,
                   feature_tags_json AS featureTagsJson,
                   business_line_code AS businessLineCode,
                   environment_code AS environmentCode,
                   name,
                   system_name AS systemName,
                   host,
                   port,
                   database_schema AS databaseSchema,
                   username,
                   secret_ref AS secretRef,
                   password_encrypted AS passwordEncrypted,
                   ssh_password_encrypted AS sshPasswordEncrypted,
                   ssh_bastion_enabled AS sshBastionEnabled,
                   bastion_id AS bastionId,
                   ssh_bastion_host AS sshBastionHost,
                   ssh_bastion_port AS sshBastionPort,
                   ssh_bastion_user AS sshBastionUser,
                   ssh_bastion_password_encrypted AS sshBastionPasswordEncrypted,
                   ssh_identity_file AS sshIdentityFile,
                   allowed_services_json AS allowedServicesJson,
                   allowed_log_paths_json AS allowedLogPathsJson,
                   profiles_json AS profilesJson,
                   enabled,
                   remark,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM mcp_resource_config
            WHERE id = #{id}
            """)
    McpResourceEntity findById(@Param("id") Long id);

    @Select("""
            SELECT id,
                   resource_type AS resourceType,
                   target_key AS targetKey,
                   public_resource AS publicResource,
                   feature_tags_json AS featureTagsJson,
                   business_line_code AS businessLineCode,
                   environment_code AS environmentCode,
                   name,
                   system_name AS systemName,
                   host,
                   port,
                   database_schema AS databaseSchema,
                   username,
                   secret_ref AS secretRef,
                   password_encrypted AS passwordEncrypted,
                   ssh_password_encrypted AS sshPasswordEncrypted,
                   ssh_bastion_enabled AS sshBastionEnabled,
                   bastion_id AS bastionId,
                   ssh_bastion_host AS sshBastionHost,
                   ssh_bastion_port AS sshBastionPort,
                   ssh_bastion_user AS sshBastionUser,
                   ssh_bastion_password_encrypted AS sshBastionPasswordEncrypted,
                   ssh_identity_file AS sshIdentityFile,
                   allowed_services_json AS allowedServicesJson,
                   allowed_log_paths_json AS allowedLogPathsJson,
                   profiles_json AS profilesJson,
                   enabled,
                   remark,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM mcp_resource_config
            WHERE target_key = #{targetKey}
            LIMIT 1
            """)
    McpResourceEntity findByTargetKey(@Param("targetKey") String targetKey);

    @Insert("""
            INSERT INTO mcp_resource_config (
                resource_type,
                target_key,
                public_resource,
                feature_tags_json,
                business_line_code,
                environment_code,
                name,
                system_name,
                host,
                port,
                database_schema,
                username,
                secret_ref,
                password_encrypted,
                ssh_password_encrypted,
                ssh_bastion_enabled,
                bastion_id,
                ssh_bastion_host,
                ssh_bastion_port,
                ssh_bastion_user,
                ssh_bastion_password_encrypted,
                ssh_identity_file,
                allowed_services_json,
                allowed_log_paths_json,
                profiles_json,
                enabled,
                remark
            ) VALUES (
                #{resourceType},
                #{targetKey},
                #{publicResource},
                #{featureTagsJson},
                #{businessLineCode},
                #{environmentCode},
                #{name},
                #{systemName},
                #{host},
                #{port},
                #{databaseSchema},
                #{username},
                #{secretRef},
                #{passwordEncrypted},
                #{sshPasswordEncrypted},
                #{sshBastionEnabled},
                #{bastionId},
                #{sshBastionHost},
                #{sshBastionPort},
                #{sshBastionUser},
                #{sshBastionPasswordEncrypted},
                #{sshIdentityFile},
                #{allowedServicesJson},
                #{allowedLogPathsJson},
                #{profilesJson},
                #{enabled},
                #{remark}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(McpResourceEntity entity);

    @Update("""
            UPDATE mcp_resource_config
            SET resource_type = #{resourceType},
                target_key = #{targetKey},
                public_resource = #{publicResource},
                feature_tags_json = #{featureTagsJson},
                business_line_code = #{businessLineCode},
                environment_code = #{environmentCode},
                name = #{name},
                system_name = #{systemName},
                host = #{host},
                port = #{port},
                database_schema = #{databaseSchema},
                username = #{username},
                secret_ref = #{secretRef},
                password_encrypted = #{passwordEncrypted},
                ssh_password_encrypted = #{sshPasswordEncrypted},
                ssh_bastion_enabled = #{sshBastionEnabled},
                bastion_id = #{bastionId},
                ssh_bastion_host = #{sshBastionHost},
                ssh_bastion_port = #{sshBastionPort},
                ssh_bastion_user = #{sshBastionUser},
                ssh_bastion_password_encrypted = #{sshBastionPasswordEncrypted},
                ssh_identity_file = #{sshIdentityFile},
                allowed_services_json = #{allowedServicesJson},
                allowed_log_paths_json = #{allowedLogPathsJson},
                profiles_json = #{profilesJson},
                enabled = #{enabled},
                remark = #{remark},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int update(McpResourceEntity entity);

    @Delete("DELETE FROM mcp_resource_config WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Select("""
            SELECT business_line_code
            FROM mcp_resource_business_line
            WHERE resource_id = #{resourceId}
            ORDER BY id ASC
            """)
    List<String> findBusinessLineCodes(@Param("resourceId") Long resourceId);

    @Insert("""
            INSERT INTO mcp_resource_business_line (
                resource_id,
                business_line_code
            ) VALUES (
                #{resourceId},
                #{businessLineCode}
            )
            """)
    void insertBusinessLineBinding(@Param("resourceId") Long resourceId,
                                   @Param("businessLineCode") String businessLineCode);

    @Delete("DELETE FROM mcp_resource_business_line WHERE resource_id = #{resourceId}")
    int deleteBusinessLineBindings(@Param("resourceId") Long resourceId);
}
