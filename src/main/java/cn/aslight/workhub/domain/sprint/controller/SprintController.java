package cn.aslight.workhub.domain.sprint.controller;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.common.api.PageResponse;
import cn.aslight.workhub.domain.sprint.dto.SprintDetailResponse;
import cn.aslight.workhub.domain.sprint.dto.SprintSaveRequest;
import cn.aslight.workhub.domain.sprint.dto.SprintSummaryResponse;
import cn.aslight.workhub.domain.sprint.service.SprintService;
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
@RequestMapping("/api/sprints")
public class SprintController {

    private final SprintService sprintService;

    public SprintController(SprintService sprintService) {
        this.sprintService = sprintService;
    }

    @GetMapping
    public ApiResponse<PageResponse<SprintSummaryResponse>> list(@RequestParam(required = false) Long projectId,
                                                                 @RequestParam(required = false) String status) {
        List<SprintSummaryResponse> items = sprintService.list(projectId, status);
        return ApiResponse.success(new PageResponse<>(items.size(), items));
    }

    @GetMapping("/{id}")
    public ApiResponse<SprintDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(sprintService.detail(id));
    }

    @PostMapping
    public ApiResponse<SprintDetailResponse> create(@Valid @RequestBody SprintSaveRequest request) {
        return ApiResponse.success(sprintService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<SprintDetailResponse> update(@PathVariable Long id,
                                                    @Valid @RequestBody SprintSaveRequest request) {
        return ApiResponse.success(sprintService.update(id, request));
    }
}
