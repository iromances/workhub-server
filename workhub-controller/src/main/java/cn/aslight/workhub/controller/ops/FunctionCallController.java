package cn.aslight.workhub.controller.ops;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.model.ops.FuncCallModels;
import cn.aslight.workhub.model.ops.FuncCallModels.FuncCallExecuteRequest;
import cn.aslight.workhub.model.ops.FuncCallModels.FuncCallExecuteResponse;
import cn.aslight.workhub.model.ops.FuncCallModels.FuncCallSqlItem;
import cn.aslight.workhub.model.ops.FuncCallModels.FuncCallSqlSaveRequest;
import cn.aslight.workhub.service.ops.FunctionCallSqlService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** REQ-9f3c2a84：Function Call 登记/维护/试运行接口。 */
@RestController
@RequestMapping("/api/ops/function-call")
public class FunctionCallController {

    private final FunctionCallSqlService functionCallSqlService;

    public FunctionCallController(FunctionCallSqlService functionCallSqlService) {
        this.functionCallSqlService = functionCallSqlService;
    }

    @GetMapping("/sql")
    @PreAuthorize("hasAuthority('ops:function-call:view') or hasAuthority('ops:function-call:manage')")
    public ApiResponse<List<FuncCallSqlItem>> list(@RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) Boolean enabled) {
        return ApiResponse.success(functionCallSqlService.list(keyword, enabled));
    }

    @PostMapping("/sql")
    @PreAuthorize("hasAuthority('ops:function-call:manage')")
    public ApiResponse<FuncCallSqlItem> create(@RequestBody FuncCallSqlSaveRequest request) {
        return ApiResponse.success(functionCallSqlService.create(request));
    }

    @PutMapping("/sql/{id}")
    @PreAuthorize("hasAuthority('ops:function-call:manage')")
    public ApiResponse<FuncCallSqlItem> update(@PathVariable Long id, @RequestBody FuncCallSqlSaveRequest request) {
        return ApiResponse.success(functionCallSqlService.update(id, request));
    }

    @PutMapping("/sql/{id}/enabled")
    @PreAuthorize("hasAuthority('ops:function-call:manage')")
    public ApiResponse<FuncCallSqlItem> updateEnabled(@PathVariable Long id,
                                                      @RequestBody FuncCallModels.FuncCallEnabledUpdateRequest request) {
        return ApiResponse.success(functionCallSqlService.updateEnabled(id, request.enabled()));
    }

    @PostMapping("/execute")
    @PreAuthorize("hasAuthority('ops:function-call:execute') or hasAuthority('ops:function-call:manage')")
    public ApiResponse<FuncCallExecuteResponse> execute(@RequestBody FuncCallExecuteRequest request) {
        return ApiResponse.success(functionCallSqlService.tryRun(request));
    }

    /** REQ-b4d7e6f2：执行审计查询。 */
    @GetMapping("/audits")
    @PreAuthorize("hasAuthority('ops:function-call:view') or hasAuthority('ops:function-call:manage')")
    public ApiResponse<FuncCallModels.FuncCallAuditPage> audits(
            @RequestParam(required = false) String sqlCode,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(functionCallSqlService.audits(sqlCode, source, status, startDate, endDate, page, pageSize));
    }
}
