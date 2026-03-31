package cn.aslight.workhub.domain.attachment.mapper;

import cn.aslight.workhub.domain.attachment.model.AttachmentEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AttachmentMapper {

    @Insert("""
            INSERT INTO pm_attachment (
                biz_type,
                biz_id,
                file_name,
                storage_path,
                content_type
            ) VALUES (
                #{bizType},
                #{bizId},
                #{fileName},
                #{storagePath},
                #{contentType}
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
}
