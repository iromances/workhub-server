package cn.aslight.workhub.service.project;

import cn.aslight.workhub.model.project.ProjectDetailResponse;
import cn.aslight.workhub.model.project.ProjectGroupEntity;
import cn.aslight.workhub.model.project.ProjectGroupResponse;
import cn.aslight.workhub.model.project.ProjectGroupSaveRequest;
import cn.aslight.workhub.model.project.ProjectSaveRequest;
import cn.aslight.workhub.model.project.ProjectSummaryResponse;
import cn.aslight.workhub.dao.project.ProjectGroupMapper;
import cn.aslight.workhub.dao.project.ProjectMapper;
import cn.aslight.workhub.model.project.ProjectEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 项目业务服务。
 */
@Service
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final ProjectGroupMapper projectGroupMapper;

    public ProjectService(ProjectMapper projectMapper,
                          ProjectGroupMapper projectGroupMapper) {
        this.projectMapper = projectMapper;
        this.projectGroupMapper = projectGroupMapper;
    }

    public List<ProjectSummaryResponse> list(String status, String keyword) {
        return projectMapper.findAll(trimToNull(status), trimToNull(keyword));
    }

    public List<ProjectGroupResponse> listGroups(String keyword) {
        return projectGroupMapper.findAll(trimToNull(keyword)).stream()
                .map(this::toGroupResponse)
                .toList();
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
    public ProjectGroupResponse createGroup(ProjectGroupSaveRequest request) {
        ensureProjectGroupNameAvailable(request.getGroupName(), null);
        ProjectGroupEntity entity = toGroupEntity(new ProjectGroupEntity(), request);
        projectGroupMapper.insert(entity);
        return toGroupResponse(requireExistingGroup(entity.getId()));
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

    @Transactional
    public void delete(Long id) {
        ProjectEntity existing = requireExisting(id);
        if (projectMapper.countWorkItems(id) > 0) {
            throw new IllegalArgumentException("项目已有工作项，不能删除");
        }
        if (projectMapper.countSprints(id) > 0) {
            throw new IllegalArgumentException("项目已有迭代，不能删除");
        }
        if (projectMapper.countReleases(id) > 0) {
            throw new IllegalArgumentException("项目已有版本，不能删除");
        }
        if (projectMapper.countPaymentBindings(id) > 0) {
            throw new IllegalArgumentException("项目已有支付绑定，不能删除");
        }
        if (projectMapper.countDevelopmentAnalyses(id) > 0) {
            throw new IllegalArgumentException("项目已有研发评估记录，不能删除");
        }
        projectMapper.deleteById(existing.getId());
    }

    @Transactional
    public ProjectGroupResponse updateGroup(Long id, ProjectGroupSaveRequest request) {
        requireExistingGroup(id);
        ensureProjectGroupNameAvailable(request.getGroupName(), id);
        ProjectGroupEntity entity = toGroupEntity(new ProjectGroupEntity(), request);
        entity.setId(id);
        projectGroupMapper.update(entity);
        return toGroupResponse(requireExistingGroup(id));
    }

    @Transactional
    public void deleteGroup(Long id) {
        ProjectGroupEntity existing = requireExistingGroup(id);
        if (projectMapper.countByProjectGroup(existing.getGroupName()) > 0) {
            throw new IllegalArgumentException("项目组已有项目，不能删除");
        }
        if (projectGroupMapper.countMembers(existing.getGroupName()) > 0) {
            throw new IllegalArgumentException("项目组已有成员，不能删除");
        }
        projectGroupMapper.deleteById(id);
    }

    private void ensureProjectCodeAvailable(String code, Long currentId) {
        ProjectEntity existing = projectMapper.findEntityByCode(code.trim());
        if (existing != null && !existing.getId().equals(currentId)) {
            throw new IllegalArgumentException("系统编码已存在");
        }
    }

    private void ensureProjectGroupNameAvailable(String groupName, Long currentId) {
        ProjectGroupEntity existing = projectGroupMapper.findByName(groupName.trim());
        if (existing != null && !existing.getId().equals(currentId)) {
            throw new IllegalArgumentException("项目组名称已存在");
        }
    }

    private ProjectGroupEntity requireExistingGroup(Long id) {
        ProjectGroupEntity entity = projectGroupMapper.findById(id);
        if (entity == null) {
            throw new IllegalArgumentException("项目组不存在");
        }
        return entity;
    }

    private ProjectEntity toEntity(ProjectSaveRequest request) {
        ProjectEntity entity = new ProjectEntity();
        entity.setBusinessLineCode(request.getBusinessLineCode().trim());
        entity.setBusinessLineName(request.getBusinessLineName().trim());
        entity.setProjectCode(request.getCode().trim());
        entity.setProjectName(request.getName().trim());
        entity.setProjectType(request.getType().trim());
        entity.setProjectGroup(request.getGroup().trim());
        entity.setProjectStatus(request.getStatus().trim());
        entity.setOwnerUserName(request.getOwnerUserName().trim());
        entity.setDescription(trimToNull(request.getDescription()));
        return entity;
    }

    private ProjectGroupEntity toGroupEntity(ProjectGroupEntity entity, ProjectGroupSaveRequest request) {
        entity.setGroupName(request.getGroupName().trim());
        entity.setGitlabGroupName(trimToNull(request.getGitlabGroupName()));
        entity.setDescription(trimToNull(request.getDescription()));
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        return entity;
    }

    private ProjectGroupResponse toGroupResponse(ProjectGroupEntity entity) {
        return new ProjectGroupResponse(
                entity.getId(),
                entity.getGroupName(),
                entity.getGitlabGroupName(),
                entity.getDescription(),
                entity.getEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
