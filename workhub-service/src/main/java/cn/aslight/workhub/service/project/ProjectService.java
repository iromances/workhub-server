package cn.aslight.workhub.service.project;

import cn.aslight.workhub.model.project.ProjectDetailResponse;
import cn.aslight.workhub.model.project.BusinessLineEntity;
import cn.aslight.workhub.model.project.BusinessLineResponse;
import cn.aslight.workhub.model.project.BusinessLineSaveRequest;
import cn.aslight.workhub.model.project.ProjectInvolvedSystemEntity;
import cn.aslight.workhub.model.project.ProjectInvolvedSystemResponse;
import cn.aslight.workhub.model.project.ProjectInvolvedSystemSaveRequest;
import cn.aslight.workhub.model.project.ProjectSaveRequest;
import cn.aslight.workhub.model.project.ProjectSummaryResponse;
import cn.aslight.workhub.dao.project.BusinessLineMapper;
import cn.aslight.workhub.dao.project.ProjectInvolvedSystemMapper;
import cn.aslight.workhub.dao.project.ProjectMapper;
import cn.aslight.workhub.model.project.ProjectEntity;
import cn.aslight.workhub.service.intake.GitlabRepositoryService;
import cn.aslight.workhub.service.intake.GitlabRepositoryService.GitlabProjectSystem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 项目业务服务。
 */
@Service
public class ProjectService {

    public static final String SYSTEM_SCOPE_BUSINESS_LINE = "BUSINESS_LINE";
    public static final String SYSTEM_SCOPE_MIDDLE_PLATFORM = "MIDDLE_PLATFORM";
    private static final String BUSINESS_LINE_CODE_PREFIX = "BL";
    private static final int BUSINESS_LINE_CODE_WIDTH = 6;

    private final ProjectMapper projectMapper;
    private final BusinessLineMapper businessLineMapper;
    private final ProjectInvolvedSystemMapper involvedSystemMapper;
    private final GitlabRepositoryService gitlabRepositoryService;

    public ProjectService(ProjectMapper projectMapper,
                          BusinessLineMapper businessLineMapper,
                          ProjectInvolvedSystemMapper involvedSystemMapper,
                          GitlabRepositoryService gitlabRepositoryService) {
        this.projectMapper = projectMapper;
        this.businessLineMapper = businessLineMapper;
        this.involvedSystemMapper = involvedSystemMapper;
        this.gitlabRepositoryService = gitlabRepositoryService;
    }

    public List<ProjectSummaryResponse> list(String status, String keyword, String businessLine) {
        return projectMapper.findAll(trimToNull(status), trimToNull(keyword), normalizeOptionalBusinessLineCode(businessLine));
    }

    public List<BusinessLineResponse> listBusinessLines(String keyword) {
        return businessLineMapper.findAll(trimToNull(keyword)).stream()
                .map(this::toBusinessLineResponse)
                .toList();
    }

    public List<ProjectInvolvedSystemResponse> listInvolvedSystems(String systemScope,
                                                                   String businessLine,
                                                                   Boolean enabledOnly,
                                                                   String keyword) {
        String scope = normalizeSystemScope(systemScope, false);
        String normalizedBusinessLine = normalizeSystemBusinessLine(scope, businessLine);
        return involvedSystemMapper.findAll(scope, normalizedBusinessLine, Boolean.TRUE.equals(enabledOnly), trimToNull(keyword)).stream()
                .map(this::toInvolvedSystemResponse)
                .toList();
    }

    public List<ProjectInvolvedSystemResponse> listSelectableInvolvedSystems(String businessLine) {
        String businessLineCode = requireBusinessLine(businessLine).getBusinessLineCode();
        return involvedSystemMapper.findSelectableByBusinessLineCode(businessLineCode).stream()
                .map(this::toInvolvedSystemResponse)
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
    public BusinessLineResponse createBusinessLine(BusinessLineSaveRequest request) {
        ensureBusinessLineNameAvailable(request.getBusinessLineName(), null);
        BusinessLineEntity entity = toBusinessLineEntity(new BusinessLineEntity(), request);
        entity.setBusinessLineCode(nextBusinessLineCode());
        businessLineMapper.insert(entity);
        return toBusinessLineResponse(requireExistingBusinessLine(entity.getId()));
    }

    @Transactional
    public ProjectInvolvedSystemResponse createInvolvedSystem(ProjectInvolvedSystemSaveRequest request) {
        ProjectInvolvedSystemEntity entity = toInvolvedSystemEntity(new ProjectInvolvedSystemEntity(), request);
        ensureInvolvedSystemNameAvailable(entity.getSystemScope(), entity.getBusinessLineCode(), entity.getSystemName(), null);
        involvedSystemMapper.insert(entity);
        return toInvolvedSystemResponse(requireExistingInvolvedSystem(entity.getId()));
    }

    @Transactional
    public List<ProjectInvolvedSystemResponse> syncInvolvedSystemsFromGit(Long businessLineId) {
        BusinessLineEntity businessLineEntity = requireExistingBusinessLine(businessLineId);
        if (Boolean.FALSE.equals(businessLineEntity.getEnabled())) {
            throw new IllegalArgumentException("业务线已停用，不能同步 Git 系统清单");
        }
        String businessLine = businessLineEntity.getBusinessLineName();
        String businessLineCode = businessLineEntity.getBusinessLineCode();
        List<GitlabProjectSystem> systems = gitlabRepositoryService.listGroupProjectSystems(businessLine);
        Set<String> handledNames = new LinkedHashSet<>();
        int sortOrder = 100;
        for (GitlabProjectSystem system : systems) {
            String systemName = requireSystemName(system.systemName());
            if (!handledNames.add(systemName)) {
                continue;
            }
            ProjectInvolvedSystemEntity existing = involvedSystemMapper.findByIdentity(
                    SYSTEM_SCOPE_BUSINESS_LINE,
                    businessLineCode,
                    systemName
            );
            if (existing != null) {
                sortOrder = Math.max(sortOrder, existing.getSortOrder() == null ? sortOrder : existing.getSortOrder() + 10);
                continue;
            }
            ProjectInvolvedSystemEntity entity = new ProjectInvolvedSystemEntity();
            entity.setSystemScope(SYSTEM_SCOPE_BUSINESS_LINE);
            entity.setBusinessLine(businessLine);
            entity.setBusinessLineCode(businessLineCode);
            entity.setSystemName(systemName);
            entity.setDescription(buildGitSystemDescription(system));
            entity.setEnabled(true);
            entity.setSortOrder(sortOrder);
            involvedSystemMapper.insert(entity);
            sortOrder += 10;
        }
        return listInvolvedSystems(SYSTEM_SCOPE_BUSINESS_LINE, businessLineCode, false, null);
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
    public BusinessLineResponse updateBusinessLine(Long id, BusinessLineSaveRequest request) {
        BusinessLineEntity existing = requireExistingBusinessLine(id);
        ensureBusinessLineNameAvailable(request.getBusinessLineName(), id);
        BusinessLineEntity entity = toBusinessLineEntity(new BusinessLineEntity(), request);
        entity.setId(id);
        entity.setBusinessLineCode(existing.getBusinessLineCode());
        businessLineMapper.update(entity);
        return toBusinessLineResponse(requireExistingBusinessLine(id));
    }

    @Transactional
    public ProjectInvolvedSystemResponse updateInvolvedSystem(Long id, ProjectInvolvedSystemSaveRequest request) {
        ProjectInvolvedSystemEntity existing = requireExistingInvolvedSystem(id);
        ProjectInvolvedSystemEntity entity = toInvolvedSystemEntity(new ProjectInvolvedSystemEntity(), request);
        entity.setId(id);
        if (!existing.getSystemName().equals(entity.getSystemName())
                && involvedSystemMapper.countDevelopmentAnalysisUsage(existing.getSystemName()) > 0) {
            throw new IllegalArgumentException("系统已被研发任务使用，不能修改系统名称");
        }
        ensureInvolvedSystemNameAvailable(entity.getSystemScope(), entity.getBusinessLineCode(), entity.getSystemName(), id);
        involvedSystemMapper.update(entity);
        return toInvolvedSystemResponse(requireExistingInvolvedSystem(id));
    }

    @Transactional
    public void deleteBusinessLine(Long id) {
        BusinessLineEntity existing = requireExistingBusinessLine(id);
        if (projectMapper.countByBusinessLine(existing.getBusinessLineCode()) > 0) {
            throw new IllegalArgumentException("业务线已有项目，不能删除");
        }
        if (businessLineMapper.countMembers(existing.getBusinessLineName()) > 0) {
            throw new IllegalArgumentException("业务线已有成员，不能删除");
        }
        if (involvedSystemMapper.countByBusinessLine(existing.getBusinessLineCode()) > 0) {
            throw new IllegalArgumentException("业务线已有涉及系统，不能删除");
        }
        businessLineMapper.deleteById(id);
    }

    @Transactional
    public void deleteInvolvedSystem(Long id) {
        ProjectInvolvedSystemEntity existing = requireExistingInvolvedSystem(id);
        if (involvedSystemMapper.countDevelopmentAnalysisUsage(existing.getSystemName()) > 0) {
            throw new IllegalArgumentException("系统已被研发任务使用，不能删除，请改为停用");
        }
        involvedSystemMapper.deleteById(id);
    }

    private void ensureProjectCodeAvailable(String code, Long currentId) {
        ProjectEntity existing = projectMapper.findEntityByCode(code.trim());
        if (existing != null && !existing.getId().equals(currentId)) {
            throw new IllegalArgumentException("系统编码已存在");
        }
    }

    private void ensureBusinessLineNameAvailable(String businessLineName, Long currentId) {
        BusinessLineEntity existing = businessLineMapper.findByName(businessLineName.trim());
        if (existing != null && !existing.getId().equals(currentId)) {
            throw new IllegalArgumentException("业务线名称已存在");
        }
    }

    private BusinessLineEntity requireExistingBusinessLine(Long id) {
        BusinessLineEntity entity = businessLineMapper.findById(id);
        if (entity == null) {
            throw new IllegalArgumentException("业务线不存在");
        }
        return entity;
    }

    private ProjectInvolvedSystemEntity requireExistingInvolvedSystem(Long id) {
        ProjectInvolvedSystemEntity entity = involvedSystemMapper.findById(id);
        if (entity == null) {
            throw new IllegalArgumentException("涉及系统不存在");
        }
        return entity;
    }

    private ProjectEntity toEntity(ProjectSaveRequest request) {
        BusinessLineEntity businessLine = requireBusinessLine(request.getBusinessLineCode(), request.getBusinessLine());
        ProjectEntity entity = new ProjectEntity();
        entity.setProjectCode(request.getCode().trim());
        entity.setProjectName(request.getName().trim());
        entity.setProjectType(request.getType().trim());
        entity.setBusinessLine(businessLine.getBusinessLineName());
        entity.setBusinessLineCode(businessLine.getBusinessLineCode());
        entity.setProjectStatus(request.getStatus().trim());
        entity.setOwnerUserName(request.getOwnerUserName().trim());
        entity.setDescription(trimToNull(request.getDescription()));
        return entity;
    }

    private BusinessLineEntity toBusinessLineEntity(BusinessLineEntity entity, BusinessLineSaveRequest request) {
        entity.setBusinessLineName(request.getBusinessLineName().trim());
        entity.setGitlabGroupName(trimToNull(request.getGitlabGroupName()));
        entity.setDescription(trimToNull(request.getDescription()));
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        return entity;
    }

    private BusinessLineResponse toBusinessLineResponse(BusinessLineEntity entity) {
        return new BusinessLineResponse(
                entity.getId(),
                entity.getBusinessLineCode(),
                entity.getBusinessLineName(),
                entity.getGitlabGroupName(),
                entity.getDescription(),
                entity.getEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String nextBusinessLineCode() {
        String maxCode = businessLineMapper.findMaxBusinessLineCode();
        int next = 1;
        if (maxCode != null && maxCode.startsWith(BUSINESS_LINE_CODE_PREFIX)) {
            String serial = maxCode.substring(BUSINESS_LINE_CODE_PREFIX.length());
            next = Integer.parseInt(serial) + 1;
        }
        if (next > 999999) {
            throw new IllegalStateException("业务线编码已超过可用范围");
        }
        return BUSINESS_LINE_CODE_PREFIX + String.format("%0" + BUSINESS_LINE_CODE_WIDTH + "d", next);
    }

    private ProjectInvolvedSystemEntity toInvolvedSystemEntity(ProjectInvolvedSystemEntity entity,
                                                               ProjectInvolvedSystemSaveRequest request) {
        String scope = normalizeSystemScope(request.getSystemScope(), true);
        BusinessLineEntity businessLine = normalizeSystemBusinessLine(scope, request.getBusinessLineCode(), request.getBusinessLine());
        entity.setSystemScope(scope);
        entity.setBusinessLine(businessLine.getBusinessLineName());
        entity.setBusinessLineCode(businessLine.getBusinessLineCode());
        entity.setSystemName(requireSystemName(request.getSystemName()));
        entity.setDescription(trimToNull(request.getDescription()));
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        return entity;
    }

    private ProjectInvolvedSystemResponse toInvolvedSystemResponse(ProjectInvolvedSystemEntity entity) {
        return new ProjectInvolvedSystemResponse(
                entity.getId(),
                entity.getSystemScope(),
                entity.getBusinessLineCode(),
                resolveBusinessLineName(entity.getBusinessLineCode(), entity.getBusinessLine()),
                entity.getSystemName(),
                entity.getDescription(),
                entity.getEnabled(),
                entity.getSortOrder(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private void ensureInvolvedSystemNameAvailable(String systemScope, String businessLine, String systemName, Long currentId) {
        ProjectInvolvedSystemEntity existing = involvedSystemMapper.findByIdentity(systemScope, businessLine, systemName);
        if (existing != null && !existing.getId().equals(currentId)) {
            throw new IllegalArgumentException("涉及系统名称已存在");
        }
    }

    private String buildGitSystemDescription(GitlabProjectSystem system) {
        String source = trimToNull(system.pathWithNamespace());
        if (source == null) {
            source = trimToNull(system.webUrl());
        }
        if (source == null) {
            source = trimToNull(system.path());
        }
        return source == null ? "从 GitLab 仓库同步" : "从 GitLab 仓库同步：" + source;
    }

    private String normalizeSystemScope(String scope, boolean required) {
        String normalized = trimToNull(scope);
        if (normalized == null) {
            if (required) {
                throw new IllegalArgumentException("系统范围不能为空");
            }
            return null;
        }
        String upper = normalized.toUpperCase();
        if (SYSTEM_SCOPE_BUSINESS_LINE.equals(upper) || SYSTEM_SCOPE_MIDDLE_PLATFORM.equals(upper)) {
            return upper;
        }
        throw new IllegalArgumentException("系统范围不合法");
    }

    private String normalizeSystemBusinessLine(String scope, String businessLine) {
        return normalizeSystemBusinessLine(scope, null, businessLine).getBusinessLineCode();
    }

    private BusinessLineEntity normalizeSystemBusinessLine(String scope, String businessLineCode, String businessLine) {
        if (scope == null) {
            BusinessLineEntity entity = new BusinessLineEntity();
            entity.setBusinessLineCode(trimToNull(businessLineCode));
            entity.setBusinessLineName(trimToNull(businessLine));
            return entity;
        }
        if (SYSTEM_SCOPE_MIDDLE_PLATFORM.equals(scope)) {
            BusinessLineEntity entity = new BusinessLineEntity();
            entity.setBusinessLineCode("");
            entity.setBusinessLineName("");
            return entity;
        }
        return requireBusinessLine(businessLineCode, businessLine);
    }

    private String requireBusinessLineName(String businessLine) {
        return requireBusinessLine(businessLine).getBusinessLineName();
    }

    private BusinessLineEntity requireBusinessLine(String businessLine) {
        return requireBusinessLine(null, businessLine);
    }

    private BusinessLineEntity requireBusinessLine(String businessLineCode, String businessLineName) {
        String normalizedCode = trimToNull(businessLineCode);
        if (normalizedCode != null) {
            BusinessLineEntity byCode = businessLineMapper.findByCode(normalizedCode);
            if (byCode == null) {
                throw new IllegalArgumentException("业务线不存在");
            }
            return byCode;
        }
        String normalized = trimToNull(businessLineName);
        if (normalized == null) {
            throw new IllegalArgumentException("业务线不能为空");
        }
        BusinessLineEntity byCode = businessLineMapper.findByCode(normalized);
        if (byCode != null) {
            return byCode;
        }
        BusinessLineEntity existing = businessLineMapper.findByName(normalized);
        if (existing == null) {
            throw new IllegalArgumentException("业务线不存在");
        }
        return existing;
    }

    private String normalizeOptionalBusinessLineCode(String businessLine) {
        String normalized = trimToNull(businessLine);
        return normalized == null ? null : requireBusinessLine(normalized).getBusinessLineCode();
    }

    private String resolveBusinessLineName(String businessLineCode, String fallbackName) {
        String normalizedCode = trimToNull(businessLineCode);
        if (normalizedCode == null) {
            return fallbackName;
        }
        BusinessLineEntity entity = businessLineMapper.findByCode(normalizedCode);
        return entity == null ? fallbackName : entity.getBusinessLineName();
    }

    private String requireSystemName(String systemName) {
        String normalized = trimToNull(systemName);
        if (normalized == null) {
            throw new IllegalArgumentException("系统名称不能为空");
        }
        if (normalized.length() > 128) {
            throw new IllegalArgumentException("系统名称不能超过 128 个字符");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
