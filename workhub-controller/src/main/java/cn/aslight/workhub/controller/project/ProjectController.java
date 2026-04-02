package cn.aslight.workhub.controller.project;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.common.api.PageResponse;
import cn.aslight.workhub.model.project.ProjectDetailResponse;
import cn.aslight.workhub.model.project.ProjectSaveRequest;
import cn.aslight.workhub.model.project.ProjectSummaryResponse;
import cn.aslight.workhub.service.project.ProjectService;
import jakarta.validation.Valid;
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
    public ApiResponse<PageResponse<ProjectSummaryResponse>> list(@RequestParam(required = false) String status,
                                                                  @RequestParam(required = false) String keyword) {
        List<ProjectSummaryResponse> items = projectService.list(status, keyword);
        return ApiResponse.success(new PageResponse<>(items.size(), items));
    }

    /**
     * 查询详情数据。
     *
     * @param id 业务 ID
     * @return 详情结果
     */
    @GetMapping("/{id}")
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
    public ApiResponse<ProjectDetailResponse> update(@PathVariable Long id,
                                                     @Valid @RequestBody ProjectSaveRequest request) {
        return ApiResponse.success(projectService.update(id, request));
    }
}
