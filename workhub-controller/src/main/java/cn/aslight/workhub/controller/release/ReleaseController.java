package cn.aslight.workhub.controller.release;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.common.api.PageResponse;
import cn.aslight.workhub.model.release.ReleaseDetailResponse;
import cn.aslight.workhub.model.release.ReleaseSaveRequest;
import cn.aslight.workhub.model.release.ReleaseSummaryResponse;
import cn.aslight.workhub.service.release.ReleaseService;
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
 * 版本接口控制器。
 */
@RestController
@RequestMapping("/api/releases")
public class ReleaseController {

    private final ReleaseService releaseService;

    public ReleaseController(ReleaseService releaseService) {
        this.releaseService = releaseService;
    }

    /**
     * 查询列表数据。
     *
     * @return 列表结果
     */
    @GetMapping
    public ApiResponse<PageResponse<ReleaseSummaryResponse>> list(@RequestParam(required = false) Long projectId,
                                                                  @RequestParam(required = false) String status) {
        List<ReleaseSummaryResponse> items = releaseService.list(projectId, status);
        return ApiResponse.success(new PageResponse<>(items.size(), items));
    }

    /**
     * 查询详情数据。
     *
     * @param id 业务 ID
     * @return 详情结果
     */
    @GetMapping("/{id}")
    public ApiResponse<ReleaseDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(releaseService.detail(id));
    }

    /**
     * 新建业务数据。
     *
     * @param request 保存请求
     * @return 新建结果
     */
    @PostMapping
    public ApiResponse<ReleaseDetailResponse> create(@Valid @RequestBody ReleaseSaveRequest request) {
        return ApiResponse.success(releaseService.create(request));
    }

    /**
     * 更新业务数据。
     *
     * @param id 业务 ID
     * @param request 保存请求
     * @return 更新结果
     */
    @PutMapping("/{id}")
    public ApiResponse<ReleaseDetailResponse> update(@PathVariable Long id,
                                                     @Valid @RequestBody ReleaseSaveRequest request) {
        return ApiResponse.success(releaseService.update(id, request));
    }
}
