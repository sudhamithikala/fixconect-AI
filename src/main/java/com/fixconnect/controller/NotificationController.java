package com.fixconnect.controller;

import com.fixconnect.common.MessageResponse;
import com.fixconnect.dto.CommonDtos.NotificationListResponse;
import com.fixconnect.dto.CommonDtos.NotificationResponse;
import com.fixconnect.security.CurrentUser;
import com.fixconnect.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "12. Notifications")
public class NotificationController {

    private final NotificationService notifications;
    private final CurrentUser currentUser;

    public NotificationController(NotificationService notifications, CurrentUser currentUser) {
        this.notifications = notifications;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "My notifications (customer or provider)")
    public NotificationListResponse list(@RequestParam(defaultValue = "false") boolean unreadOnly) {
        return notifications.list(currentUser.id(), unreadOnly);
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark one notification as read")
    public NotificationResponse read(@PathVariable Long id) {
        return notifications.markRead(currentUser.id(), id);
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark all notifications as read")
    public MessageResponse readAll() {
        int n = notifications.markAllRead(currentUser.id());
        return MessageResponse.ok(n + " notification(s) marked as read");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a notification")
    public MessageResponse delete(@PathVariable Long id) {
        notifications.delete(currentUser.id(), id);
        return MessageResponse.ok("Notification deleted");
    }
}
