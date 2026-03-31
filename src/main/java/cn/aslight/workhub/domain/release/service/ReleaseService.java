package cn.aslight.workhub.domain.release.service;

import cn.aslight.workhub.domain.project.service.ProjectService;
import cn.aslight.workhub.domain.release.dto.ReleaseDetailResponse;
import cn.aslight.workhub.domain.release.dto.ReleaseSaveRequest;
import cn.aslight.workhub.domain.release.dto.ReleaseSummaryResponse;
import cn.aslight.workhub.domain.release.mapper.ReleaseMapper;
import cn.aslight.workhub.domain.release.model.ReleaseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReleaseService {

    private final ReleaseMapper releaseMapper;
    private final ProjectService projectService;

    public ReleaseService(ReleaseMapper releaseMapper, ProjectService projectService) {
        this.releaseMapper = releaseMapper;
        this.projectService = projectService;
    }

    public List<ReleaseSummaryResponse> list(Long projectId, String status) {
        return releaseMapper.findAll(projectId, trimToNull(status));
    }

    public ReleaseDetailResponse detail(Long id) {
        ReleaseDetailResponse detail = releaseMapper.findDetailById(id);
        if (detail == null) {
            throw new IllegalArgumentException("版本不存在");
        }
        return detail;
    }

    public ReleaseEntity requireExisting(Long id) {
        ReleaseEntity entity = releaseMapper.findEntityById(id);
        if (entity == null) {
            throw new IllegalArgumentException("版本不存在");
        }
        return entity;
    }

    @Transactional
    public ReleaseDetailResponse create(ReleaseSaveRequest request) {
        validatePlanAndReleaseTime(request);
        projectService.requireExisting(request.getProjectId());
        ReleaseEntity entity = toEntity(request);
        releaseMapper.insert(entity);
        return detail(entity.getId());
    }

    @Transactional
    public ReleaseDetailResponse update(Long id, ReleaseSaveRequest request) {
        requireExisting(id);
        validatePlanAndReleaseTime(request);
        projectService.requireExisting(request.getProjectId());
        ReleaseEntity entity = toEntity(request);
        entity.setId(id);
        releaseMapper.update(entity);
        return detail(id);
    }

    private ReleaseEntity toEntity(ReleaseSaveRequest request) {
        ReleaseEntity entity = new ReleaseEntity();
        entity.setProjectId(request.getProjectId());
        entity.setReleaseName(request.getName().trim());
        entity.setReleaseVersion(request.getVersion().trim());
        entity.setReleaseStatus(request.getStatus().trim());
        entity.setPlannedAt(request.getPlannedAt());
        entity.setReleasedAt(request.getReleasedAt());
        return entity;
    }

    private void validatePlanAndReleaseTime(ReleaseSaveRequest request) {
        if (request.getPlannedAt() != null
                && request.getReleasedAt() != null
                && request.getReleasedAt().isBefore(request.getPlannedAt())) {
            throw new IllegalArgumentException("版本发布时间不能早于计划时间");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
