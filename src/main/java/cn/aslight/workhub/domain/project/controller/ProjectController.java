package cn.aslight.workhub.domain.project.controller;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.common.api.PageResponse;
import cn.aslight.workhub.domain.project.dto.ProjectDetailResponse;
import cn.aslight.workhub.domain.project.dto.ProjectSaveRequest;
import cn.aslight.workhub.domain.project.dto.ProjectSummaryResponse;
import cn.aslight.workhub.domain.project.service.ProjectService;
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

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ProjectSummaryResponse>> list(@RequestParam(required = false) String status,
                                                                  @RequestParam(required = false) String keyword) {
        List<ProjectSummaryResponse> items = projectService.list(status, keyword);
        return ApiResponse.success(new PageResponse<>(items.size(), items));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProjectDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(projectService.detail(id));
    }

    @PostMapping
    public ApiResponse<ProjectDetailResponse> create(@Valid @RequestBody ProjectSaveRequest request) {
        return ApiResponse.success(projectService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProjectDetailResponse> update(@PathVariable Long id,
                                                     @Valid @RequestBody ProjectSaveRequest request) {
        return ApiResponse.success(projectService.update(id, request));
    }
}
