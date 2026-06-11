package com.paycore.notification.controller;

import com.paycore.notification.domain.NotificationStatus;
import com.paycore.notification.domain.NotificationType;
import com.paycore.notification.dto.NotificationLogResponse;
import com.paycore.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NotificationControllerTest {

    private MockMvc mockMvc;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new NotificationController(notificationService))
                .build();
    }

    @Test
    void getNotificationsByPaymentId_shouldReturnPaymentNotifications() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        NotificationLogResponse firstNotification = createNotificationLogResponse(
                paymentId,
                merchantId,
                "Test Merchant",
                "AUTHORIZED",
                NotificationStatus.SENT
        );

        NotificationLogResponse secondNotification = createNotificationLogResponse(
                paymentId,
                merchantId,
                "Test Merchant",
                "FAILED",
                NotificationStatus.SENT
        );

        when(notificationService.getNotificationsByPaymentId(paymentId))
                .thenReturn(List.of(firstNotification, secondNotification));

        mockMvc.perform(get("/api/notifications/payments/{paymentId}", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(firstNotification.id().toString()))
                .andExpect(jsonPath("$[0].paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$[0].merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$[0].merchantName").value("Test Merchant"))
                .andExpect(jsonPath("$[0].notificationType").value("PAYMENT_RESULT"))
                .andExpect(jsonPath("$[0].paymentStatus").value("AUTHORIZED"))
                .andExpect(jsonPath("$[0].amount").value(1000.00))
                .andExpect(jsonPath("$[0].currency").value("TRY"))
                .andExpect(jsonPath("$[0].orderId").value("ORDER-NOTIFICATION-CONTROLLER-1001"))
                .andExpect(jsonPath("$[0].providerReferenceId").value("PROVIDER-REF-1001"))
                .andExpect(jsonPath("$[0].providerResponseCode").value("00"))
                .andExpect(jsonPath("$[0].providerResponseMessage").value("APPROVED"))
                .andExpect(jsonPath("$[0].status").value("SENT"))
                .andExpect(jsonPath("$[0].simulatedTarget").value("merchant-webhook://" + merchantId))
                .andExpect(jsonPath("$[0].message").exists())
                .andExpect(jsonPath("$[1].paymentStatus").value("FAILED"))
                .andExpect(jsonPath("$[1].status").value("SENT"));

        verify(notificationService).getNotificationsByPaymentId(paymentId);
    }

    @Test
    void getNotificationsByMerchantId_shouldReturnMerchantNotifications() throws Exception {
        UUID merchantId = UUID.randomUUID();

        NotificationLogResponse firstNotification = createNotificationLogResponse(
                UUID.randomUUID(),
                merchantId,
                "Test Merchant",
                "AUTHORIZED",
                NotificationStatus.SENT
        );

        NotificationLogResponse secondNotification = createNotificationLogResponse(
                UUID.randomUUID(),
                merchantId,
                "Test Merchant",
                "FAILED",
                NotificationStatus.SENT
        );

        when(notificationService.getNotificationsByMerchantId(merchantId))
                .thenReturn(List.of(firstNotification, secondNotification));

        mockMvc.perform(get("/api/notifications/merchants/{merchantId}", merchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$[0].merchantName").value("Test Merchant"))
                .andExpect(jsonPath("$[0].notificationType").value("PAYMENT_RESULT"))
                .andExpect(jsonPath("$[0].paymentStatus").value("AUTHORIZED"))
                .andExpect(jsonPath("$[0].status").value("SENT"))
                .andExpect(jsonPath("$[1].merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$[1].merchantName").value("Test Merchant"))
                .andExpect(jsonPath("$[1].notificationType").value("PAYMENT_RESULT"))
                .andExpect(jsonPath("$[1].paymentStatus").value("FAILED"))
                .andExpect(jsonPath("$[1].status").value("SENT"));

        verify(notificationService).getNotificationsByMerchantId(merchantId);
    }

    @Test
    void getNotificationsByPaymentId_shouldReturnEmptyList_whenPaymentHasNoNotifications() throws Exception {
        UUID paymentId = UUID.randomUUID();

        when(notificationService.getNotificationsByPaymentId(paymentId))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/notifications/payments/{paymentId}", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verify(notificationService).getNotificationsByPaymentId(paymentId);
    }

    @Test
    void getNotificationsByMerchantId_shouldReturnEmptyList_whenMerchantHasNoNotifications() throws Exception {
        UUID merchantId = UUID.randomUUID();

        when(notificationService.getNotificationsByMerchantId(merchantId))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/notifications/merchants/{merchantId}", merchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verify(notificationService).getNotificationsByMerchantId(merchantId);
    }

    @Test
    void getNotificationsByPaymentId_shouldReturnBadRequest_whenPaymentIdIsInvalidUuid() throws Exception {
        mockMvc.perform(get("/api/notifications/payments/{paymentId}", "invalid-uuid"))
                .andExpect(status().isBadRequest());

        verify(notificationService, never()).getNotificationsByPaymentId(any(UUID.class));
    }

    @Test
    void getNotificationsByMerchantId_shouldReturnBadRequest_whenMerchantIdIsInvalidUuid() throws Exception {
        mockMvc.perform(get("/api/notifications/merchants/{merchantId}", "invalid-uuid"))
                .andExpect(status().isBadRequest());

        verify(notificationService, never()).getNotificationsByMerchantId(any(UUID.class));
    }

    private NotificationLogResponse createNotificationLogResponse(
            UUID paymentId,
            UUID merchantId,
            String merchantName,
            String paymentStatus,
            NotificationStatus status
    ) {
        LocalDateTime receivedAt = LocalDateTime.now().minusSeconds(5);
        LocalDateTime sentAt = LocalDateTime.now();

        return new NotificationLogResponse(
                UUID.randomUUID(),
                paymentId,
                merchantId,
                merchantName,
                NotificationType.PAYMENT_RESULT,
                paymentStatus,
                new BigDecimal("1000.00"),
                "TRY",
                "ORDER-NOTIFICATION-CONTROLLER-1001",
                "PROVIDER-REF-1001",
                "00",
                "APPROVED",
                status,
                "merchant-webhook://" + merchantId,
                "Payment " + paymentStatus + " for order ORDER-NOTIFICATION-CONTROLLER-1001 with amount 1000.00 TRY. Provider response: APPROVED",
                receivedAt,
                sentAt
        );
    }
}