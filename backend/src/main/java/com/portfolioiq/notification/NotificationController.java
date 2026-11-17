package com.portfolioiq.notification;

import com.portfolioiq.common.CurrentUser;
import com.portfolioiq.common.PageResponse;
import com.portfolioiq.notification.NotificationService.NotificationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** The in-app notification centre. Every operation is scoped to the signed-in user. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService service;
    private final CurrentUser currentUser;

    public NotificationController(NotificationService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<NotificationResponse> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size, Authentication auth) {
        return service.list(currentUser.id(auth), unreadOnly, page, size);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(Authentication auth) {
        return Map.of("unread", service.unreadCount(currentUser.id(auth)));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable Long id, Authentication auth) { service.markRead(currentUser.id(auth), id); }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead(Authentication auth) {
        return Map.of("marked", service.markAllRead(currentUser.id(auth)));
    }
}
