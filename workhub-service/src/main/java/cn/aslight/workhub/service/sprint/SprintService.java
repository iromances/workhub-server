package cn.aslight.workhub.service.sprint;

import cn.aslight.workhub.service.project.ProjectService;
import cn.aslight.workhub.model.sprint.SprintDetailResponse;
import cn.aslight.workhub.model.sprint.SprintSaveRequest;
import cn.aslight.workhub.model.sprint.SprintSummaryResponse;
import cn.aslight.workhub.dao.sprint.SprintMapper;
import cn.aslight.workhub.model.sprint.SprintEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 迭代业务服务。
 */
@Service
public class SprintService {

    private final SprintMapper sprintMapper;
    private final ProjectService projectService;

    public SprintService(SprintMapper sprintMapper, ProjectService projectService) {
        this.sprintMapper = sprintMapper;
        this.projectService = projectService;
    }

    public List<SprintSummaryResponse> list(Long projectId, String status) {
        return sprintMapper.findAll(projectId, trimToNull(status));
    }

    public SprintDetailResponse detail(Long id) {
        SprintDetailResponse detail = sprintMapper.findDetailById(id);
        if (detail == null) {
            throw new IllegalArgumentException("迭代不存在");
        }
        return detail;
    }

    public SprintEntity requireExisting(Long id) {
        SprintEntity entity = sprintMapper.findEntityById(id);
        if (entity == null) {
            throw new IllegalArgumentException("迭代不存在");
        }
        return entity;
    }

    @Transactional
    public SprintDetailResponse create(SprintSaveRequest request) {
        validateDateRange(request);
        projectService.requireExisting(request.getProjectId());
        SprintEntity entity = toEntity(request);
        sprintMapper.insert(entity);
        return detail(entity.getId());
    }

    @Transactional
    public SprintDetailResponse update(Long id, SprintSaveRequest request) {
        requireExisting(id);
        validateDateRange(request);
        projectService.requireExisting(request.getProjectId());
        SprintEntity entity = toEntity(request);
        entity.setId(id);
        sprintMapper.update(entity);
        return detail(id);
    }

    private SprintEntity toEntity(SprintSaveRequest request) {
        SprintEntity entity = new SprintEntity();
        entity.setProjectId(request.getProjectId());
        entity.setSprintName(request.getName().trim());
        entity.setSprintStatus(request.getStatus().trim());
        entity.setStartDate(request.getStartDate());
        entity.setEndDate(request.getEndDate());
        return entity;
    }

    private void validateDateRange(SprintSaveRequest request) {
        if (request.getStartDate() != null
                && request.getEndDate() != null
                && request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("迭代结束日期不能早于开始日期");
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
