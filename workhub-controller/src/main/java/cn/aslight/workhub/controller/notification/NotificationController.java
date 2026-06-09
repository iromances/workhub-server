package cn.aslight.workhub.controller.notification;

import cn.aslight.workhub.common.api.ApiResponse;
import cn.aslight.workhub.common.api.PageResponse;
import cn.aslight.workhub.model.notification.NotificationResponse;
import cn.aslight.workhub.service.notification.NotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> list(@RequestParam(defaultValue = "50") int limit,
                                                                Principal principal) {
        List<NotificationResponse> items = notificationService.list(principal.getName(), limit);
        return ApiResponse.success(new PageResponse<>(items.size(), items));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Integer>> unreadCount(Principal principal) {
        return ApiResponse.success(Map.of("count", notificationService.unreadCount(principal.getName())));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id, Principal principal) {
        notificationService.markRead(id, principal.getName());
        return ApiResponse.success(null);
    }

    @PostMapping("/read-all")
    public ApiResponse<Void> markAllRead(Principal principal) {
        notificationService.markAllRead(principal.getName());
        return ApiResponse.success(null);
    }
}
