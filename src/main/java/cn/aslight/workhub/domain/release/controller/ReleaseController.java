package cn.aslight.workhub.domain.release.controller;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.common.api.PageResponse;
import cn.aslight.workhub.domain.release.dto.ReleaseDetailResponse;
import cn.aslight.workhub.domain.release.dto.ReleaseSaveRequest;
import cn.aslight.workhub.domain.release.dto.ReleaseSummaryResponse;
import cn.aslight.workhub.domain.release.service.ReleaseService;
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
@RequestMapping("/api/releases")
public class ReleaseController {

    private final ReleaseService releaseService;

    public ReleaseController(ReleaseService releaseService) {
        this.releaseService = releaseService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ReleaseSummaryResponse>> list(@RequestParam(required = false) Long projectId,
                                                                  @RequestParam(required = false) String status) {
        List<ReleaseSummaryResponse> items = releaseService.list(projectId, status);
        return ApiResponse.success(new PageResponse<>(items.size(), items));
    }

    @GetMapping("/{id}")
    public ApiResponse<ReleaseDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(releaseService.detail(id));
    }

    @PostMapping
    public ApiResponse<ReleaseDetailResponse> create(@Valid @RequestBody ReleaseSaveRequest request) {
        return ApiResponse.success(releaseService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ReleaseDetailResponse> update(@PathVariable Long id,
                                                     @Valid @RequestBody ReleaseSaveRequest request) {
        return ApiResponse.success(releaseService.update(id, request));
    }
}
