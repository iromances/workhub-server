package cn.aslight.workhub.dao.attachment;

import cn.aslight.workhub.model.attachment.AttachmentEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 附件数据访问接口。
 */
@Mapper
public interface AttachmentMapper {

    @Insert("""
            INSERT INTO pm_attachment (
                biz_type,
                biz_id,
                file_name,
                storage_path,
                content_type,
                file_content,
                file_size,
                file_sha256
            ) VALUES (
                #{bizType},
                #{bizId},
                #{fileName},
                #{storagePath},
                #{contentType},
                #{fileContent},
                #{fileSize},
                #{fileSha256}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AttachmentEntity entity);

    @Select({
            "<script>",
            "SELECT id,",
            "biz_type,",
            "biz_id,",
            "file_name,",
            "storage_path,",
            "content_type,",
            "created_at",
            "FROM pm_attachment",
            "WHERE biz_id = #{bizId}",
            "AND biz_type IN",
            "<foreach collection='bizTypes' item='bizType' open='(' separator=',' close=')'>",
            "#{bizType}",
            "</foreach>",
            "ORDER BY created_at ASC, id ASC",
            "</script>"
    })
    List<AttachmentEntity> findByBiz(@Param("bizId") Long bizId, @Param("bizTypes") List<String> bizTypes);

    @Select({
            "<script>",
            "SELECT id,",
            "biz_type,",
            "biz_id,",
            "file_name,",
            "file_size,",
            "file_sha256,",
            "created_at",
            "FROM pm_attachment",
            "WHERE biz_id = #{bizId}",
            "AND biz_type IN",
            "<foreach collection='bizTypes' item='bizType' open='(' separator=',' close=')'>",
            "#{bizType}",
            "</foreach>",
            "ORDER BY created_at ASC, id ASC",
            "</script>"
    })
    List<AttachmentEntity> findDigestsByBiz(@Param("bizId") Long bizId, @Param("bizTypes") List<String> bizTypes);

    @Select({
            "<script>",
            "SELECT id,",
            "biz_type,",
            "biz_id,",
            "file_name,",
            "storage_path,",
            "content_type,",
            "file_content,",
            "file_size,",
            "file_sha256,",
            "created_at",
            "FROM pm_attachment",
            "WHERE biz_id = #{bizId}",
            "AND biz_type IN",
            "<foreach collection='bizTypes' item='bizType' open='(' separator=',' close=')'>",
            "#{bizType}",
            "</foreach>",
            "ORDER BY created_at ASC, id ASC",
            "</script>"
    })
    List<AttachmentEntity> findByBizWithContent(@Param("bizId") Long bizId,
                                                @Param("bizTypes") List<String> bizTypes);

    /**
     * 按业务类型查多个业务对象下的附件元数据，不读取附件内容。
     *
     * @param bizType 业务类型
     * @param bizIds 业务 ID 列表，不能为空
     * @return 附件元数据，按业务 ID 和上传顺序排列
     */
    @Select({
            "<script>",
            "SELECT id,",
            "biz_type,",
            "biz_id,",
            "file_name,",
            "content_type,",
            "created_at",
            "FROM pm_attachment",
            "WHERE biz_type = #{bizType}",
            "AND biz_id IN",
            "<foreach collection='bizIds' item='bizId' open='(' separator=',' close=')'>",
            "#{bizId}",
            "</foreach>",
            "ORDER BY biz_id ASC, created_at ASC, id ASC",
            "</script>"
    })
    List<AttachmentEntity> findByBizIds(@Param("bizType") String bizType, @Param("bizIds") List<Long> bizIds);

    @Select("""
            SELECT id,
                   biz_type,
                   biz_id,
                   file_name,
                   storage_path,
                   content_type,
                   created_at
            FROM pm_attachment
            WHERE id = #{id}
            """)
    AttachmentEntity findById(Long id);

    @Select("""
            SELECT id,
                   biz_type,
                   biz_id,
                   file_name,
                   storage_path,
                   content_type,
                   file_content,
                   file_size,
                   file_sha256,
                   created_at
            FROM pm_attachment
            WHERE id = #{id}
            """)
    AttachmentEntity findByIdWithContent(Long id);

    /**
     * 按 ID 删除附件记录。
     *
     * @param id 附件 ID
     * @return 影响行数
     */
    @Delete("""
            DELETE FROM pm_attachment
            WHERE id = #{id}
            """)
    int deleteById(Long id);

    /**
     * 删除一个业务对象下的全部附件。
     *
     * @param bizType 业务类型
     * @param bizId 业务 ID
     * @return 影响行数
     */
    @Delete("""
            DELETE FROM pm_attachment
            WHERE biz_type = #{bizType}
              AND biz_id = #{bizId}
            """)
    int deleteByBiz(@Param("bizType") String bizType, @Param("bizId") Long bizId);

    /**
     * 删除一个业务对象下的指定附件，不会删除其他业务对象或其他业务类型的附件。
     *
     * @param bizType 业务类型
     * @param bizId 业务 ID
     * @param ids 附件 ID 列表，不能为空
     * @return 影响行数
     */
    @Delete({
            "<script>",
            "DELETE FROM pm_attachment",
            "WHERE biz_type = #{bizType}",
            "AND biz_id = #{bizId}",
            "AND id IN",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>",
            "#{id}",
            "</foreach>",
            "</script>"
    })
    int deleteByBizAndIds(@Param("bizType") String bizType,
                          @Param("bizId") Long bizId,
                          @Param("ids") List<Long> ids);

    /**
     * 替换附件文件元数据。
     *
     * @param id 附件 ID
     * @param fileName 新原始文件名
     * @param contentType 新内容类型
     * @param fileContent 新文件内容
     * @param fileSize 新文件大小
     * @param fileSha256 新文件 SHA-256
     * @return 影响行数
     */
    @Update("""
            UPDATE pm_attachment
            SET file_name = #{fileName},
                storage_path = NULL,
                content_type = #{contentType},
                file_content = #{fileContent},
                file_size = #{fileSize},
                file_sha256 = #{fileSha256}
            WHERE id = #{id}
            """)
    int updateFile(@Param("id") Long id,
                   @Param("fileName") String fileName,
                   @Param("contentType") String contentType,
                   @Param("fileContent") byte[] fileContent,
                   @Param("fileSize") Long fileSize,
                   @Param("fileSha256") String fileSha256);
}
