package cn.aslight.workhub.controller.system;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.common.api.PageResponse;
import cn.aslight.workhub.model.system.DeveloperResourceResponse;
import cn.aslight.workhub.model.system.DeveloperResourceSaveRequest;
import cn.aslight.workhub.model.system.UserOptionResponse;
import cn.aslight.workhub.service.system.DeveloperResourceService;
import jakarta.validation.Valid;
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
import java.util.Map;

/**
 * 系统探活接口控制器。
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final DeveloperResourceService developerResourceService;

    public SystemController(DeveloperResourceService developerResourceService) {
        this.developerResourceService = developerResourceService;
    }

    /**
     * 健康检查接口。
     *
     * @return 固定的系统存活信息
     */
    @GetMapping("/ping")
    public ApiResponse<Map<String, String>> ping() {
        return ApiResponse.success(Map.of(
                "status", "UP",
                "application", "workhub-server"
        ));
    }

    /**
     * 查询研发人员资源候选项。
     *
     * @return 研发人员资源列表
     */
    @GetMapping("/developer-options")
    public ApiResponse<PageResponse<UserOptionResponse>> listDeveloperOptions() {
        List<UserOptionResponse> items = developerResourceService.listOptions();
        return ApiResponse.success(new PageResponse<>(items.size(), items));
    }

    /**
     * 查询研发人员资源列表。
     *
     * @param keyword 关键字
     * @return 研发人员资源列表
     */
    @GetMapping("/developers")
    public ApiResponse<PageResponse<DeveloperResourceResponse>> listDevelopers(@RequestParam(required = false) String keyword) {
        List<DeveloperResourceResponse> items = developerResourceService.list(keyword);
        return ApiResponse.success(new PageResponse<>(items.size(), items));
    }

    /**
     * 新增研发人员资源。
     *
     * @param request 保存请求
     * @return 新增后的研发人员
     */
    @PostMapping("/developers")
    public ApiResponse<DeveloperResourceResponse> createDeveloper(@Valid @RequestBody DeveloperResourceSaveRequest request) {
        return ApiResponse.success(developerResourceService.create(request));
    }

    /**
     * 更新研发人员资源。
     *
     * @param id 研发人员 ID
     * @param request 保存请求
     * @return 更新后的研发人员
     */
    @PutMapping("/developers/{id}")
    public ApiResponse<DeveloperResourceResponse> updateDeveloper(@PathVariable Long id,
                                                                  @Valid @RequestBody DeveloperResourceSaveRequest request) {
        return ApiResponse.success(developerResourceService.update(id, request));
    }

    /**
     * 删除研发人员资源。
     *
     * @param id 研发人员 ID
     * @return 删除结果
     */
    @DeleteMapping("/developers/{id}")
    public ApiResponse<Void> deleteDeveloper(@PathVariable Long id) {
        developerResourceService.delete(id);
        return ApiResponse.success(null);
    }
}
