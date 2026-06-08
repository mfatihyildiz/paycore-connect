package com.paycore.notification.controller;

import com.paycore.notification.dto.NotificationLogResponse;
import com.paycore.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/payments/{paymentId}")
    public List<NotificationLogResponse> getNotificationsByPaymentId(@PathVariable UUID paymentId) {
        return notificationService.getNotificationsByPaymentId(paymentId);
    }

    @GetMapping("/merchants/{merchantId}")
    public List<NotificationLogResponse> getNotificationsByMerchantId(@PathVariable UUID merchantId) {
        return notificationService.getNotificationsByMerchantId(merchantId);
    }
}