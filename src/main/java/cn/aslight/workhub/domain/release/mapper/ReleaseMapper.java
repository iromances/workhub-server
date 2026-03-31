package cn.aslight.workhub.domain.release.mapper;

import cn.aslight.workhub.domain.release.dto.ReleaseDetailResponse;
import cn.aslight.workhub.domain.release.dto.ReleaseSummaryResponse;
import cn.aslight.workhub.domain.release.model.ReleaseEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ReleaseMapper {

    @Select({
            "<script>",
            "SELECT r.id,",
            "r.project_id AS projectId,",
            "p.project_name AS projectName,",
            "r.release_name AS name,",
            "r.release_version AS version,",
            "r.release_status AS status,",
            "r.planned_at AS plannedAt,",
            "r.released_at AS releasedAt",
            "FROM pm_release r",
            "JOIN pm_project p ON p.id = r.project_id",
            "<where>",
            "<if test='projectId != null'>",
            "AND r.project_id = #{projectId}",
            "</if>",
            "<if test='status != null and status != \"\"'>",
            "AND r.release_status = #{status}",
            "</if>",
            "</where>",
            "ORDER BY r.id DESC",
            "</script>"
    })
    List<ReleaseSummaryResponse> findAll(@Param("projectId") Long projectId, @Param("status") String status);

    @Select("""
            SELECT r.id,
                   r.project_id AS projectId,
                   p.project_name AS projectName,
                   r.release_name AS name,
                   r.release_version AS version,
                   r.release_status AS status,
                   r.planned_at AS plannedAt,
                   r.released_at AS releasedAt,
                   r.created_at AS createdAt,
                   r.updated_at AS updatedAt
            FROM pm_release r
            JOIN pm_project p ON p.id = r.project_id
            WHERE r.id = #{id}
            """)
    ReleaseDetailResponse findDetailById(Long id);

    @Select("""
            SELECT id,
                   project_id,
                   release_name,
                   release_version,
                   release_status,
                   planned_at,
                   released_at
            FROM pm_release
            WHERE id = #{id}
            """)
    ReleaseEntity findEntityById(Long id);

    @Insert("""
            INSERT INTO pm_release (
                project_id,
                release_name,
                release_version,
                release_status,
                planned_at,
                released_at
            ) VALUES (
                #{projectId},
                #{releaseName},
                #{releaseVersion},
                #{releaseStatus},
                #{plannedAt},
                #{releasedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ReleaseEntity entity);

    @Update("""
            UPDATE pm_release
            SET project_id = #{projectId},
                release_name = #{releaseName},
                release_version = #{releaseVersion},
                release_status = #{releaseStatus},
                planned_at = #{plannedAt},
                released_at = #{releasedAt}
            WHERE id = #{id}
            """)
    int update(ReleaseEntity entity);
}
