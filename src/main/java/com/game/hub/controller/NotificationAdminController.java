package com.game.hub.controller;

import com.game.hub.entity.SystemNotification;
import com.game.hub.repository.SystemNotificationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping({"/notification-admin", "/admin/notices"})
public class NotificationAdminController {
    private final SystemNotificationRepository systemNotificationRepository;

    public NotificationAdminController(SystemNotificationRepository systemNotificationRepository) {
        this.systemNotificationRepository = systemNotificationRepository;
    }

    @GetMapping
    public String page(Model model) {
        return "redirect:/admin";
    }

    @ResponseBody
    @GetMapping("/api")
    public List<SystemNotification> index() {
        return systemNotificationRepository.findAllByOrderByCreatedAtDesc();
    }

    @ResponseBody
    @PostMapping
    public Map<String, Object> create(@RequestBody CreateNotificationRequest request) {
        if (request.content() == null || request.content().isBlank()) {
            return Map.of("success", false, "error", "Content is required");
        }

        SystemNotification n = new SystemNotification();
        n.setContent(request.content());
        systemNotificationRepository.save(n);
        return Map.of("success", true, "notification", n);
    }

    @ResponseBody
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        SystemNotification n = systemNotificationRepository.findById(id).orElse(null);
        if (n == null) {
            return Map.of("success", false, "error", "Notification not found");
        }
        systemNotificationRepository.delete(n);
        return Map.of("success", true);
    }

    public record CreateNotificationRequest(String content) {
    }
}
