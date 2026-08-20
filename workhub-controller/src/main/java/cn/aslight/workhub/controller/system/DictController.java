package cn.aslight.workhub.controller.system;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.model.system.DictQueryRequest;
import cn.aslight.workhub.model.system.DictResponse;
import cn.aslight.workhub.service.system.DictService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class DictController {
    private final DictService service;
    public DictController(DictService service) { this.service = service; }
    @PostMapping("/dict/list")
    @PreAuthorize("hasAuthority('system:ai-config:view') or hasAuthority('system:ai-config:manage')")
    public ApiResponse<List<DictResponse>> list(@RequestBody(required = false) DictQueryRequest request) {
        return ApiResponse.success(service.query(request));
    }
}
