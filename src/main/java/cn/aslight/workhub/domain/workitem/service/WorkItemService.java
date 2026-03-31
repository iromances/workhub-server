package cn.aslight.workhub.domain.workitem.service;

import cn.aslight.workhub.domain.project.service.ProjectService;
import cn.aslight.workhub.domain.release.model.ReleaseEntity;
import cn.aslight.workhub.domain.release.service.ReleaseService;
import cn.aslight.workhub.domain.sprint.model.SprintEntity;
import cn.aslight.workhub.domain.sprint.service.SprintService;
import cn.aslight.workhub.domain.workitem.dto.WorkItemCreateRequest;
import cn.aslight.workhub.domain.workitem.dto.WorkItemDetailResponse;
import cn.aslight.workhub.domain.workitem.dto.WorkItemSummaryResponse;
import cn.aslight.workhub.domain.workitem.dto.WorkItemUpdateRequest;
import cn.aslight.workhub.domain.workitem.mapper.WorkItemMapper;
import cn.aslight.workhub.domain.workitem.model.WorkItemEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class WorkItemService {

    private static final DateTimeFormatter NO_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final WorkItemMapper workItemMapper;
    private final ProjectService projectService;
    private final SprintService sprintService;
    private final ReleaseService releaseService;

    public WorkItemService(WorkItemMapper workItemMapper,
                           ProjectService projectService,
                           SprintService sprintService,
                           ReleaseService releaseService) {
        this.workItemMapper = workItemMapper;
        this.projectService = projectService;
        this.sprintService = sprintService;
        this.releaseService = releaseService;
    }

    public List<WorkItemSummaryResponse> list(Long projectId, String status, String type, String keyword) {
        return workItemMapper.findAll(projectId, trimToNull(status), trimToNull(type), trimToNull(keyword));
    }

    public WorkItemDetailResponse detail(Long id) {
        WorkItemDetailResponse detail = workItemMapper.findDetailById(id);
        if (detail == null) {
            throw new IllegalArgumentException("工作项不存在");
        }
        return detail;
    }

    public WorkItemEntity requireExisting(Long id) {
        WorkItemEntity entity = workItemMapper.findEntityById(id);
        if (entity == null) {
            throw new IllegalArgumentException("工作项不存在");
        }
        return entity;
    }

    @Transactional
    public WorkItemDetailResponse create(WorkItemCreateRequest request, String operatorUserName) {
        validateDateRange(request.getPlannedStartAt(), request.getPlannedEndAt());
        validateRelations(request.getProjectId(), request.getSprintId(), request.getReleaseId());

        WorkItemEntity entity = new WorkItemEntity();
        entity.setWorkItemNo(generateWorkItemNo(request.getType()));
        entity.setProjectId(request.getProjectId());
        entity.setSprintId(request.getSprintId());
        entity.setReleaseId(request.getReleaseId());
        entity.setWorkItemType(request.getType().trim());
        entity.setTitle(request.getTitle().trim());
        entity.setDescription(trimToNull(request.getDescription()));
        entity.setSourceType(request.getSourceType().trim());
        entity.setSourceChannel(request.getSourceChannel().trim());
        entity.setPriority(request.getPriority().trim());
        entity.setUrgency(trimToNull(request.getUrgency()));
        entity.setStatus(WorkItemStatusRules.INITIAL_STATUS);
        entity.setCreatorUserName(operatorUserName);
        entity.setOwnerUserName(request.getOwnerUserName().trim());
        entity.setFollowerUserName(request.getFollowerUserName().trim());
        entity.setProposerName(trimToNull(request.getProposerName()));
        entity.setAcceptanceCriteria(trimToNull(request.getAcceptanceCriteria()));
        entity.setPlannedStartAt(request.getPlannedStartAt());
        entity.setPlannedEndAt(request.getPlannedEndAt());
        entity.setFinishedAt(null);

        workItemMapper.insert(entity);
        return detail(entity.getId());
    }

    @Transactional
    public WorkItemDetailResponse update(Long id, WorkItemUpdateRequest request) {
        WorkItemEntity existing = requireExisting(id);
        validateDateRange(request.getPlannedStartAt(), request.getPlannedEndAt());

        existing.setTitle(request.getTitle().trim());
        existing.setDescription(trimToNull(request.getDescription()));
        existing.setPriority(request.getPriority().trim());
        existing.setUrgency(trimToNull(request.getUrgency()));
        existing.setProposerName(trimToNull(request.getProposerName()));
        existing.setAcceptanceCriteria(trimToNull(request.getAcceptanceCriteria()));
        existing.setPlannedStartAt(request.getPlannedStartAt());
        existing.setPlannedEndAt(request.getPlannedEndAt());

        workItemMapper.updateEditableFields(existing);
        return detail(id);
    }

    @Transactional
    public WorkItemDetailResponse updateStatus(Long id, String status, LocalDateTime finishedAt) {
        requireExisting(id);
        workItemMapper.updateStatus(id, status, finishedAt);
        return detail(id);
    }

    private void validateRelations(Long projectId, Long sprintId, Long releaseId) {
        projectService.requireExisting(projectId);
        if (sprintId != null) {
            SprintEntity sprint = sprintService.requireExisting(sprintId);
            if (!projectId.equals(sprint.getProjectId())) {
                throw new IllegalArgumentException("工作项迭代不属于当前项目");
            }
        }
        if (releaseId != null) {
            ReleaseEntity release = releaseService.requireExisting(releaseId);
            if (!projectId.equals(release.getProjectId())) {
                throw new IllegalArgumentException("工作项版本不属于当前项目");
            }
        }
    }

    private void validateDateRange(LocalDateTime plannedStartAt, LocalDateTime plannedEndAt) {
        if (plannedStartAt != null && plannedEndAt != null && plannedEndAt.isBefore(plannedStartAt)) {
            throw new IllegalArgumentException("计划结束时间不能早于开始时间");
        }
    }

    private String generateWorkItemNo(String type) {
        String prefix = switch (type.trim()) {
            case "需求" -> "REQ";
            case "缺陷" -> "BUG";
            case "运维" -> "OPS";
            case "任务" -> "TASK";
            default -> "WH";
        };
        return prefix + "-" + NO_TIME_FORMATTER.format(LocalDateTime.now())
                + ThreadLocalRandom.current().nextInt(1000, 9999);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
