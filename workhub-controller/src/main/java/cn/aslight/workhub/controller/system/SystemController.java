package cn.aslight.workhub.controller.system;

import cn.aslight.workhub.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

/**
 * 系统探活接口控制器。
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

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
}
