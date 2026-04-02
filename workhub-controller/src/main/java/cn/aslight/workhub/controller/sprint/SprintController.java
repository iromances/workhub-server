package cn.aslight.workhub.controller.sprint;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.common.api.PageResponse;
import cn.aslight.workhub.model.sprint.SprintDetailResponse;
import cn.aslight.workhub.model.sprint.SprintSaveRequest;
import cn.aslight.workhub.model.sprint.SprintSummaryResponse;
import cn.aslight.workhub.service.sprint.SprintService;
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
 * 迭代接口控制器。
 */
@RestController
@RequestMapping("/api/sprints")
public class SprintController {

    private final SprintService sprintService;

    public SprintController(SprintService sprintService) {
        this.sprintService = sprintService;
    }

    /**
     * 查询列表数据。
     *
     * @return 列表结果
     */
    @GetMapping
    public ApiResponse<PageResponse<SprintSummaryResponse>> list(@RequestParam(required = false) Long projectId,
                                                                 @RequestParam(required = false) String status) {
        List<SprintSummaryResponse> items = sprintService.list(projectId, status);
        return ApiResponse.success(new PageResponse<>(items.size(), items));
    }

    /**
     * 查询详情数据。
     *
     * @param id 业务 ID
     * @return 详情结果
     */
    @GetMapping("/{id}")
    public ApiResponse<SprintDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(sprintService.detail(id));
    }

    /**
     * 新建业务数据。
     *
     * @param request 保存请求
     * @return 新建结果
     */
    @PostMapping
    public ApiResponse<SprintDetailResponse> create(@Valid @RequestBody SprintSaveRequest request) {
        return ApiResponse.success(sprintService.create(request));
    }

    /**
     * 更新业务数据。
     *
     * @param id 业务 ID
     * @param request 保存请求
     * @return 更新结果
     */
    @PutMapping("/{id}")
    public ApiResponse<SprintDetailResponse> update(@PathVariable Long id,
                                                    @Valid @RequestBody SprintSaveRequest request) {
        return ApiResponse.success(sprintService.update(id, request));
    }
}
