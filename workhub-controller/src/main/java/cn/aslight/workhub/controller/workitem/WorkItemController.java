package cn.aslight.workhub.controller.workitem;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.common.api.PageResponse;
import cn.aslight.workhub.model.workitem.WorkItemCreateRequest;
import cn.aslight.workhub.model.workitem.WorkItemDetailResponse;
import cn.aslight.workhub.model.workitem.WorkItemAssignRequest;
import cn.aslight.workhub.model.workitem.WorkItemFollowUpRequest;
import cn.aslight.workhub.model.workitem.WorkItemFollowUpResponse;
import cn.aslight.workhub.model.workitem.WorkItemSummaryResponse;
import cn.aslight.workhub.model.workitem.WorkItemTransitionRequest;
import cn.aslight.workhub.model.workitem.WorkItemTransitionResponse;
import cn.aslight.workhub.model.workitem.WorkItemUpdateRequest;
import cn.aslight.workhub.service.workitem.WorkItemAssignmentService;
import cn.aslight.workhub.service.workitem.WorkItemFollowUpService;
import cn.aslight.workhub.service.workitem.WorkItemService;
import cn.aslight.workhub.service.workitem.WorkItemTransitionService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
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
 * 工作项接口控制器。
 */
@RestController
@RequestMapping("/api/work-items")
public class WorkItemController {

    private final WorkItemService workItemService;
    private final WorkItemAssignmentService workItemAssignmentService;
    private final WorkItemFollowUpService workItemFollowUpService;
    private final WorkItemTransitionService workItemTransitionService;

    public WorkItemController(WorkItemService workItemService,
                              WorkItemAssignmentService workItemAssignmentService,
                              WorkItemFollowUpService workItemFollowUpService,
                              WorkItemTransitionService workItemTransitionService) {
        this.workItemService = workItemService;
        this.workItemAssignmentService = workItemAssignmentService;
        this.workItemFollowUpService = workItemFollowUpService;
        this.workItemTransitionService = workItemTransitionService;
    }

    /**
     * 查询列表数据。
     *
     * @return 列表结果
     */
    @GetMapping
    public ApiResponse<PageResponse<WorkItemSummaryResponse>> list(@RequestParam(required = false) Long projectId,
                                                                   @RequestParam(required = false) String status,
                                                                   @RequestParam(required = false) String type,
                                                                   @RequestParam(required = false) String keyword) {
        List<WorkItemSummaryResponse> items = workItemService.list(projectId, status, type, keyword);
        return ApiResponse.success(new PageResponse<>(items.size(), items));
    }

    /**
     * 查询详情数据。
     *
     * @param id 业务 ID
     * @return 详情结果
     */
    @GetMapping("/{id}")
    public ApiResponse<WorkItemDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(workItemService.detail(id));
    }

    /**
     * 新建业务数据。
     *
     * @param request 保存请求
     * @return 新建结果
     */
    @PostMapping
    public ApiResponse<WorkItemDetailResponse> create(@Valid @RequestBody WorkItemCreateRequest request,
                                                      Authentication authentication) {
        return ApiResponse.success(workItemService.create(request, authentication.getName()));
    }

    /**
     * 更新业务数据。
     *
     * @param id 业务 ID
     * @param request 保存请求
     * @return 更新结果
     */
    @PutMapping("/{id}")
    public ApiResponse<WorkItemDetailResponse> update(@PathVariable Long id,
                                                      @Valid @RequestBody WorkItemUpdateRequest request) {
        return ApiResponse.success(workItemService.update(id, request));
    }

    /**
     * 指派工作项负责人和跟进人。
     *
     * @param id 工作项 ID
     * @param request 指派请求
     * @param authentication 当前认证信息
     * @return 更新后的工作项详情
     */
    @PostMapping("/{id}/assign")
    public ApiResponse<WorkItemDetailResponse> assign(@PathVariable Long id,
                                                      @Valid @RequestBody WorkItemAssignRequest request,
                                                      Authentication authentication) {
        return ApiResponse.success(workItemAssignmentService.assign(id, request, authentication.getName()));
    }

    /**
     * 查询工作项跟踪记录。
     *
     * @param id 工作项 ID
     * @return 跟踪记录列表
     */
    @GetMapping("/{id}/follow-ups")
    public ApiResponse<PageResponse<WorkItemFollowUpResponse>> listFollowUps(@PathVariable Long id) {
        List<WorkItemFollowUpResponse> items = workItemFollowUpService.list(id);
        return ApiResponse.success(new PageResponse<>(items.size(), items));
    }

    /**
     * 新增工作项跟踪记录。
     *
     * @param id 工作项 ID
     * @param request 跟踪记录请求
     * @param authentication 当前认证信息
     * @return 新建后的跟踪记录
     */
    @PostMapping("/{id}/follow-ups")
    public ApiResponse<WorkItemFollowUpResponse> addFollowUp(@PathVariable Long id,
                                                             @Valid @RequestBody WorkItemFollowUpRequest request,
                                                             Authentication authentication) {
        return ApiResponse.success(workItemFollowUpService.add(id, request, authentication.getName()));
    }

    /**
     * 查询工作项状态流转日志。
     *
     * @param id 工作项 ID
     * @return 状态流转列表
     */
    @GetMapping("/{id}/transitions")
    public ApiResponse<PageResponse<WorkItemTransitionResponse>> listTransitions(@PathVariable Long id) {
        List<WorkItemTransitionResponse> items = workItemTransitionService.list(id);
        return ApiResponse.success(new PageResponse<>(items.size(), items));
    }

    /**
     * 执行工作项状态流转。
     *
     * @param id 工作项 ID
     * @param request 流转请求
     * @param authentication 当前认证信息
     * @return 新建后的流转记录
     */
    @PostMapping("/{id}/transitions")
    public ApiResponse<WorkItemTransitionResponse> transition(@PathVariable Long id,
                                                              @Valid @RequestBody WorkItemTransitionRequest request,
                                                              Authentication authentication) {
        return ApiResponse.success(workItemTransitionService.transition(id, request, authentication.getName()));
    }
}
