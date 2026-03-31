package cn.aslight.workhub.domain.workitem.controller;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.common.api.PageResponse;
import cn.aslight.workhub.domain.workitem.dto.WorkItemCreateRequest;
import cn.aslight.workhub.domain.workitem.dto.WorkItemDetailResponse;
import cn.aslight.workhub.domain.workitem.dto.WorkItemAssignRequest;
import cn.aslight.workhub.domain.workitem.dto.WorkItemFollowUpRequest;
import cn.aslight.workhub.domain.workitem.dto.WorkItemFollowUpResponse;
import cn.aslight.workhub.domain.workitem.dto.WorkItemSummaryResponse;
import cn.aslight.workhub.domain.workitem.dto.WorkItemTransitionRequest;
import cn.aslight.workhub.domain.workitem.dto.WorkItemTransitionResponse;
import cn.aslight.workhub.domain.workitem.dto.WorkItemUpdateRequest;
import cn.aslight.workhub.domain.workitem.service.WorkItemAssignmentService;
import cn.aslight.workhub.domain.workitem.service.WorkItemFollowUpService;
import cn.aslight.workhub.domain.workitem.service.WorkItemService;
import cn.aslight.workhub.domain.workitem.service.WorkItemTransitionService;
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

    @GetMapping
    public ApiResponse<PageResponse<WorkItemSummaryResponse>> list(@RequestParam(required = false) Long projectId,
                                                                   @RequestParam(required = false) String status,
                                                                   @RequestParam(required = false) String type,
                                                                   @RequestParam(required = false) String keyword) {
        List<WorkItemSummaryResponse> items = workItemService.list(projectId, status, type, keyword);
        return ApiResponse.success(new PageResponse<>(items.size(), items));
    }

    @GetMapping("/{id}")
    public ApiResponse<WorkItemDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(workItemService.detail(id));
    }

    @PostMapping
    public ApiResponse<WorkItemDetailResponse> create(@Valid @RequestBody WorkItemCreateRequest request,
                                                      Authentication authentication) {
        return ApiResponse.success(workItemService.create(request, authentication.getName()));
    }

    @PutMapping("/{id}")
    public ApiResponse<WorkItemDetailResponse> update(@PathVariable Long id,
                                                      @Valid @RequestBody WorkItemUpdateRequest request) {
        return ApiResponse.success(workItemService.update(id, request));
    }

    @PostMapping("/{id}/assign")
    public ApiResponse<WorkItemDetailResponse> assign(@PathVariable Long id,
                                                      @Valid @RequestBody WorkItemAssignRequest request,
                                                      Authentication authentication) {
        return ApiResponse.success(workItemAssignmentService.assign(id, request, authentication.getName()));
    }

    @GetMapping("/{id}/follow-ups")
    public ApiResponse<PageResponse<WorkItemFollowUpResponse>> listFollowUps(@PathVariable Long id) {
        List<WorkItemFollowUpResponse> items = workItemFollowUpService.list(id);
        return ApiResponse.success(new PageResponse<>(items.size(), items));
    }

    @PostMapping("/{id}/follow-ups")
    public ApiResponse<WorkItemFollowUpResponse> addFollowUp(@PathVariable Long id,
                                                             @Valid @RequestBody WorkItemFollowUpRequest request,
                                                             Authentication authentication) {
        return ApiResponse.success(workItemFollowUpService.add(id, request, authentication.getName()));
    }

    @GetMapping("/{id}/transitions")
    public ApiResponse<PageResponse<WorkItemTransitionResponse>> listTransitions(@PathVariable Long id) {
        List<WorkItemTransitionResponse> items = workItemTransitionService.list(id);
        return ApiResponse.success(new PageResponse<>(items.size(), items));
    }

    @PostMapping("/{id}/transitions")
    public ApiResponse<WorkItemTransitionResponse> transition(@PathVariable Long id,
                                                              @Valid @RequestBody WorkItemTransitionRequest request,
                                                              Authentication authentication) {
        return ApiResponse.success(workItemTransitionService.transition(id, request, authentication.getName()));
    }
}
