package cn.aslight.workhub.controller.project;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.common.api.PageResponse;
import cn.aslight.workhub.model.project.ProjectDetailResponse;
import cn.aslight.workhub.model.project.BusinessLineResponse;
import cn.aslight.workhub.model.project.BusinessLineSaveRequest;
import cn.aslight.workhub.model.project.ProjectInvolvedSystemResponse;
import cn.aslight.workhub.model.project.ProjectInvolvedSystemSaveRequest;
import cn.aslight.workhub.model.project.ProjectSaveRequest;
import cn.aslight.workhub.model.project.ProjectSummaryResponse;
import cn.aslight.workhub.service.project.ProjectService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/**
 * 项目接口控制器。
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /**
     * 查询列表数据。
     *
     * @return 列表结果
     */
    @GetMapping
    @PreAuthorize("hasAuthority('project:project:view') or hasAuthority('project:project:manage')")
    public ApiResponse<PageResponse<ProjectSummaryResponse>> list(@RequestParam(required = false) String status,
                                                                  @RequestParam(required = false) String keyword,
                                                                  @RequestParam(required = false) String businessLine,
                                                                  @RequestParam(required = false) Integer page,
                                                                  @RequestParam(required = false) Integer pageSize) {
        List<ProjectSummaryResponse> items = projectService.list(status, keyword, businessLine);
        return ApiResponse.success(page(items, page, pageSize));
    }

    /**
     * 查询业务线列表。
     *
     * @param keyword 关键字
     * @param page 页码
     * @param pageSize 每页数量
     * @return 业务线列表
     */
    @GetMapping("/business-lines")
    @PreAuthorize("hasAuthority('project:business-line:view') or hasAuthority('project:business-line:manage')")
    public ApiResponse<PageResponse<BusinessLineResponse>> listBusinessLines(@RequestParam(required = false) String keyword,
                                                                             @RequestParam(required = false) Integer page,
                                                                             @RequestParam(required = false) Integer pageSize) {
        List<BusinessLineResponse> items = projectService.listBusinessLines(keyword);
        return ApiResponse.success(page(items, page, pageSize));
    }

    /**
     * 查询研发涉及系统清单。
     *
     * @param systemScope 系统范围
     * @param businessLine 业务线
     * @param enabledOnly 是否仅查启用项
     * @param keyword 关键字
     * @return 研发涉及系统清单
     */
    @GetMapping("/involved-systems")
    @PreAuthorize("hasAuthority('project:system:view') or hasAuthority('project:system:manage') or hasAuthority('project:business-line:view') or hasAuthority('project:business-line:manage')")
    public ApiResponse<PageResponse<ProjectInvolvedSystemResponse>> listInvolvedSystems(@RequestParam(required = false) String systemScope,
                                                                                        @RequestParam(required = false) String businessLine,
                                                                                        @RequestParam(required = false) Boolean enabledOnly,
                                                                                        @RequestParam(required = false) String keyword) {
        List<ProjectInvolvedSystemResponse> items = projectService.listInvolvedSystems(systemScope, businessLine, enabledOnly, keyword);
        return ApiResponse.success(new PageResponse<>(items.size(), items));
    }

    /**
     * 查询指定业务线可选择的研发涉及系统。
     *
     * @param businessLine 业务线
     * @return 业务线系统和中台系统
     */
    @GetMapping("/involved-systems/selectable")
    @PreAuthorize("hasAuthority('project:system:view') or hasAuthority('project:business-line:view') or hasAuthority('intake:record:view') or hasAuthority('payment:config:view')")
    public ApiResponse<PageResponse<ProjectInvolvedSystemResponse>> selectableInvolvedSystems(@RequestParam String businessLine) {
        List<ProjectInvolvedSystemResponse> items = projectService.listSelectableInvolvedSystems(businessLine);
        return ApiResponse.success(new PageResponse<>(items.size(), items));
    }

    /**
     * 新增研发涉及系统。
     *
     * @param request 保存请求
     * @return 研发涉及系统详情
     */
    @PostMapping("/involved-systems")
    @PreAuthorize("hasAuthority('project:system:create') or hasAuthority('project:system:manage') or hasAuthority('project:business-line:manage')")
    public ApiResponse<ProjectInvolvedSystemResponse> createInvolvedSystem(@Valid @RequestBody ProjectInvolvedSystemSaveRequest request) {
        return ApiResponse.success(projectService.createInvolvedSystem(request));
    }

    /**
     * 更新研发涉及系统。
     *
     * @param id 系统 ID
     * @param request 保存请求
     * @return 研发涉及系统详情
     */
    @PutMapping("/involved-systems/{id}")
    @PreAuthorize("hasAuthority('project:system:update') or hasAuthority('project:system:manage') or hasAuthority('project:business-line:manage')")
    public ApiResponse<ProjectInvolvedSystemResponse> updateInvolvedSystem(@PathVariable Long id,
                                                                           @Valid @RequestBody ProjectInvolvedSystemSaveRequest request) {
        return ApiResponse.success(projectService.updateInvolvedSystem(id, request));
    }

    /**
     * 删除研发涉及系统。
     *
     * @param id 系统 ID
     * @return 删除结果
     */
    @DeleteMapping("/involved-systems/{id}")
    @PreAuthorize("hasAuthority('project:system:delete') or hasAuthority('project:system:manage') or hasAuthority('project:business-line:manage')")
    public ApiResponse<Void> deleteInvolvedSystem(@PathVariable Long id) {
        projectService.deleteInvolvedSystem(id);
        return ApiResponse.success(null);
    }

    /**
     * 从业务线配置的 GitLab 组同步研发涉及系统清单。
     *
     * @param id 业务线 ID
     * @return 同步后的业务线系统清单
     */
    @PostMapping("/business-lines/{id}/involved-systems/sync-git")
    @PreAuthorize("hasAuthority('project:business-line:sync-system') or hasAuthority('project:business-line:manage')")
    public ApiResponse<PageResponse<ProjectInvolvedSystemResponse>> syncBusinessLineInvolvedSystemsFromGit(@PathVariable Long id) {
        List<ProjectInvolvedSystemResponse> items = projectService.syncInvolvedSystemsFromGit(id);
        return ApiResponse.success(new PageResponse<>(items.size(), items));
    }

    /**
     * 新增业务线。
     *
     * @param request 业务线保存请求
     * @return 业务线详情
     */
    @PostMapping("/business-lines")
    @PreAuthorize("hasAuthority('project:business-line:create') or hasAuthority('project:business-line:manage')")
    public ApiResponse<BusinessLineResponse> createBusinessLine(@Valid @RequestBody BusinessLineSaveRequest request) {
        return ApiResponse.success(projectService.createBusinessLine(request));
    }

    /**
     * 更新业务线。
     *
     * @param id      业务线 ID
     * @param request 业务线保存请求
     * @return 业务线详情
     */
    @PutMapping("/business-lines/{id}")
    @PreAuthorize("hasAuthority('project:business-line:update') or hasAuthority('project:business-line:manage')")
    public ApiResponse<BusinessLineResponse> updateBusinessLine(@PathVariable Long id,
                                                                @Valid @RequestBody BusinessLineSaveRequest request) {
        return ApiResponse.success(projectService.updateBusinessLine(id, request));
    }

    /**
     * 删除业务线。
     *
     * @param id 业务线 ID
     * @return 删除结果
     */
    @DeleteMapping("/business-lines/{id}")
    @PreAuthorize("hasAuthority('project:business-line:delete') or hasAuthority('project:business-line:manage')")
    public ApiResponse<Void> deleteBusinessLine(@PathVariable Long id) {
        projectService.deleteBusinessLine(id);
        return ApiResponse.success(null);
    }

    /**
     * 查询详情数据。
     *
     * @param id 业务 ID
     * @return 详情结果
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('project:project:view') or hasAuthority('project:project:manage')")
    public ApiResponse<ProjectDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(projectService.detail(id));
    }

    /**
     * 新建业务数据。
     *
     * @param request 保存请求
     * @return 新建结果
     */
    @PostMapping
    @PreAuthorize("hasAuthority('project:project:create') or hasAuthority('project:project:manage')")
    public ApiResponse<ProjectDetailResponse> create(@Valid @RequestBody ProjectSaveRequest request) {
        return ApiResponse.success(projectService.create(request));
    }

    /**
     * 更新业务数据。
     *
     * @param id 业务 ID
     * @param request 保存请求
     * @return 更新结果
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('project:project:update') or hasAuthority('project:project:manage')")
    public ApiResponse<ProjectDetailResponse> update(@PathVariable Long id,
                                                     @Valid @RequestBody ProjectSaveRequest request) {
        return ApiResponse.success(projectService.update(id, request));
    }

    /**
     * 删除业务数据。
     *
     * @param id 业务 ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('project:project:delete') or hasAuthority('project:project:manage')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return ApiResponse.success(null);
    }

    private <T> PageResponse<T> page(List<T> items, Integer page, Integer pageSize) {
        if (page == null && pageSize == null) {
            return new PageResponse<>(items.size(), items);
        }
        int normalizedPage = Math.max(page == null ? 1 : page, 1);
        int normalizedPageSize = Math.max(pageSize == null ? 10 : pageSize, 1);
        int fromIndex = Math.min((normalizedPage - 1) * normalizedPageSize, items.size());
        int toIndex = Math.min(fromIndex + normalizedPageSize, items.size());
        return new PageResponse<>(items.size(), items.subList(fromIndex, toIndex));
    }
}
