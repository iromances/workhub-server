package cn.aslight.workhub.domain.project.service;

import cn.aslight.workhub.domain.project.dto.ProjectDetailResponse;
import cn.aslight.workhub.domain.project.dto.ProjectSaveRequest;
import cn.aslight.workhub.domain.project.dto.ProjectSummaryResponse;
import cn.aslight.workhub.domain.project.mapper.ProjectMapper;
import cn.aslight.workhub.domain.project.model.ProjectEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProjectService {

    private final ProjectMapper projectMapper;

    public ProjectService(ProjectMapper projectMapper) {
        this.projectMapper = projectMapper;
    }

    public List<ProjectSummaryResponse> list(String status, String keyword) {
        return projectMapper.findAll(trimToNull(status), trimToNull(keyword));
    }

    public ProjectDetailResponse detail(Long id) {
        ProjectDetailResponse detail = projectMapper.findDetailById(id);
        if (detail == null) {
            throw new IllegalArgumentException("项目不存在");
        }
        return detail;
    }

    public ProjectEntity requireExisting(Long id) {
        ProjectEntity entity = projectMapper.findEntityById(id);
        if (entity == null) {
            throw new IllegalArgumentException("项目不存在");
        }
        return entity;
    }

    @Transactional
    public ProjectDetailResponse create(ProjectSaveRequest request) {
        ensureProjectCodeAvailable(request.getCode(), null);
        ProjectEntity entity = toEntity(request);
        projectMapper.insert(entity);
        return detail(entity.getId());
    }

    @Transactional
    public ProjectDetailResponse update(Long id, ProjectSaveRequest request) {
        requireExisting(id);
        ensureProjectCodeAvailable(request.getCode(), id);
        ProjectEntity entity = toEntity(request);
        entity.setId(id);
        projectMapper.update(entity);
        return detail(id);
    }

    private void ensureProjectCodeAvailable(String code, Long currentId) {
        ProjectEntity existing = projectMapper.findEntityByCode(code.trim());
        if (existing != null && !existing.getId().equals(currentId)) {
            throw new IllegalArgumentException("项目编码已存在");
        }
    }

    private ProjectEntity toEntity(ProjectSaveRequest request) {
        ProjectEntity entity = new ProjectEntity();
        entity.setProjectCode(request.getCode().trim());
        entity.setProjectName(request.getName().trim());
        entity.setProjectType(request.getType().trim());
        entity.setProjectStatus(request.getStatus().trim());
        entity.setOwnerUserName(request.getOwnerUserName().trim());
        entity.setDescription(trimToNull(request.getDescription()));
        return entity;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
