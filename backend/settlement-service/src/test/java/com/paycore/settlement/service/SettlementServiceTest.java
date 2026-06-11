package com.paycore.settlement.service;

import com.paycore.settlement.domain.Settlement;
import com.paycore.settlement.domain.SettlementStatus;
import com.paycore.settlement.dto.MerchantSettlementSummaryResponse;
import com.paycore.settlement.dto.SettlementResponse;
import com.paycore.settlement.event.PaymentEvent;
import com.paycore.settlement.exception.SettlementNotFoundException;
import com.paycore.settlement.repository.SettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock
    private SettlementRepository settlementRepository;

    private SettlementService settlementService;

    @BeforeEach
    void setUp() {
        settlementService = new SettlementService(settlementRepository);

        ReflectionTestUtils.setField(
                settlementService,
                "commissionRate",
                new BigDecimal("0.025")
        );
    }

    @Test
    void processPaymentEvent_shouldCreateSettlement_whenEventTypeIsPaymentAuthorized() {
        PaymentEvent event = createPaymentEvent(
                "PAYMENT_AUTHORIZED",
                new BigDecimal("1000.00")
        );

        when(settlementRepository.existsByEventId(event.eventId())).thenReturn(false);
        when(settlementRepository.existsByPaymentId(event.paymentId())).thenReturn(false);
        when(settlementRepository.save(any(Settlement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        settlementService.processPaymentEvent(event);

        ArgumentCaptor<Settlement> captor = ArgumentCaptor.forClass(Settlement.class);
        verify(settlementRepository).save(captor.capture());

        Settlement savedSettlement = captor.getValue();

        assertThat(savedSettlement.getEventId()).isEqualTo(event.eventId());
        assertThat(savedSettlement.getPaymentId()).isEqualTo(event.paymentId());
        assertThat(savedSettlement.getMerchantId()).isEqualTo(event.merchantId());
        assertThat(savedSettlement.getGrossAmount()).isEqualByComparingTo("1000.00");
        assertThat(savedSettlement.getCommissionRate()).isEqualByComparingTo("0.025");
        assertThat(savedSettlement.getCommissionAmount()).isEqualByComparingTo("25.00");
        assertThat(savedSettlement.getNetAmount()).isEqualByComparingTo("975.00");
        assertThat(savedSettlement.getCurrency()).isEqualTo("TRY");
        assertThat(savedSettlement.getStatus()).isEqualTo(SettlementStatus.CALCULATED);
        assertThat(savedSettlement.getSettlementDate()).isNotNull();
        assertThat(savedSettlement.getOrderId()).isEqualTo(event.orderId());
        assertThat(savedSettlement.getSourceEventType()).isEqualTo("PAYMENT_AUTHORIZED");
        assertThat(savedSettlement.getSourceEventOccurredAt()).isEqualTo(event.occurredAt());
    }

    @Test
    void processPaymentEvent_shouldIgnoreEvent_whenEventTypeIsNotPaymentAuthorized() {
        PaymentEvent event = createPaymentEvent(
                "PAYMENT_FAILED",
                new BigDecimal("1000.00")
        );

        settlementService.processPaymentEvent(event);

        verifyNoInteractions(settlementRepository);
    }

    @Test
    void processPaymentEvent_shouldNotCreateSettlement_whenEventAlreadyProcessed() {
        PaymentEvent event = createPaymentEvent(
                "PAYMENT_AUTHORIZED",
                new BigDecimal("1000.00")
        );

        when(settlementRepository.existsByEventId(event.eventId())).thenReturn(true);

        settlementService.processPaymentEvent(event);

        verify(settlementRepository).existsByEventId(event.eventId());
        verify(settlementRepository, never()).existsByPaymentId(any(UUID.class));
        verify(settlementRepository, never()).save(any(Settlement.class));
    }

    @Test
    void processPaymentEvent_shouldNotCreateSettlement_whenPaymentAlreadyHasSettlement() {
        PaymentEvent event = createPaymentEvent(
                "PAYMENT_AUTHORIZED",
                new BigDecimal("1000.00")
        );

        when(settlementRepository.existsByEventId(event.eventId())).thenReturn(false);
        when(settlementRepository.existsByPaymentId(event.paymentId())).thenReturn(true);

        settlementService.processPaymentEvent(event);

        verify(settlementRepository).existsByEventId(event.eventId());
        verify(settlementRepository).existsByPaymentId(event.paymentId());
        verify(settlementRepository, never()).save(any(Settlement.class));
    }

    @Test
    void processPaymentEvent_shouldRoundGrossCommissionAndNetAmounts() {
        PaymentEvent event = createPaymentEvent(
                "PAYMENT_AUTHORIZED",
                new BigDecimal("1000.126")
        );

        when(settlementRepository.existsByEventId(event.eventId())).thenReturn(false);
        when(settlementRepository.existsByPaymentId(event.paymentId())).thenReturn(false);
        when(settlementRepository.save(any(Settlement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        settlementService.processPaymentEvent(event);

        ArgumentCaptor<Settlement> captor = ArgumentCaptor.forClass(Settlement.class);
        verify(settlementRepository).save(captor.capture());

        Settlement savedSettlement = captor.getValue();

        assertThat(savedSettlement.getGrossAmount()).isEqualByComparingTo("1000.13");
        assertThat(savedSettlement.getCommissionAmount()).isEqualByComparingTo("25.00");
        assertThat(savedSettlement.getNetAmount()).isEqualByComparingTo("975.13");
    }

    @Test
    void getSettlementByPaymentId_shouldReturnSettlementResponse_whenSettlementExists() {
        UUID paymentId = UUID.randomUUID();
        Settlement settlement = createSettlement(paymentId, UUID.randomUUID(), new BigDecimal("1000.00"));

        when(settlementRepository.findByPaymentId(paymentId)).thenReturn(Optional.of(settlement));

        SettlementResponse response = settlementService.getSettlementByPaymentId(paymentId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(settlement.getId());
        assertThat(response.eventId()).isEqualTo(settlement.getEventId());
        assertThat(response.paymentId()).isEqualTo(settlement.getPaymentId());
        assertThat(response.merchantId()).isEqualTo(settlement.getMerchantId());
        assertThat(response.grossAmount()).isEqualByComparingTo("1000.00");
        assertThat(response.commissionRate()).isEqualByComparingTo("0.025");
        assertThat(response.commissionAmount()).isEqualByComparingTo("25.00");
        assertThat(response.netAmount()).isEqualByComparingTo("975.00");
        assertThat(response.currency()).isEqualTo("TRY");
        assertThat(response.status()).isEqualTo(SettlementStatus.CALCULATED);
    }

    @Test
    void getSettlementByPaymentId_shouldThrowException_whenSettlementDoesNotExist() {
        UUID paymentId = UUID.randomUUID();

        when(settlementRepository.findByPaymentId(paymentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> settlementService.getSettlementByPaymentId(paymentId))
                .isInstanceOf(SettlementNotFoundException.class);
    }

    @Test
    void getSettlementsByMerchantId_shouldReturnSettlementResponses() {
        UUID merchantId = UUID.randomUUID();

        Settlement firstSettlement = createSettlement(UUID.randomUUID(), merchantId, new BigDecimal("1000.00"));
        Settlement secondSettlement = createSettlement(UUID.randomUUID(), merchantId, new BigDecimal("2000.00"));

        when(settlementRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId))
                .thenReturn(List.of(firstSettlement, secondSettlement));

        List<SettlementResponse> responses = settlementService.getSettlementsByMerchantId(merchantId);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).merchantId()).isEqualTo(merchantId);
        assertThat(responses.get(1).merchantId()).isEqualTo(merchantId);
        assertThat(responses.get(0).grossAmount()).isEqualByComparingTo("1000.00");
        assertThat(responses.get(1).grossAmount()).isEqualByComparingTo("2000.00");
    }

    @Test
    void getMerchantSettlementSummary_shouldReturnZeroSummary_whenMerchantHasNoSettlements() {
        UUID merchantId = UUID.randomUUID();

        when(settlementRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId))
                .thenReturn(List.of());

        MerchantSettlementSummaryResponse response =
                settlementService.getMerchantSettlementSummary(merchantId);

        assertThat(response).isNotNull();
        assertThat(response.merchantId()).isEqualTo(merchantId);
        assertThat(response.currency()).isNull();
        assertThat(response.settlementCount()).isZero();
        assertThat(response.totalGrossAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.totalCommissionAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.totalNetAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getMerchantSettlementSummary_shouldReturnCalculatedTotals_whenMerchantHasSettlements() {
        UUID merchantId = UUID.randomUUID();

        Settlement firstSettlement = createSettlement(UUID.randomUUID(), merchantId, new BigDecimal("1000.00"));
        Settlement secondSettlement = createSettlement(UUID.randomUUID(), merchantId, new BigDecimal("2000.00"));

        when(settlementRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId))
                .thenReturn(List.of(firstSettlement, secondSettlement));

        MerchantSettlementSummaryResponse response =
                settlementService.getMerchantSettlementSummary(merchantId);

        assertThat(response).isNotNull();
        assertThat(response.merchantId()).isEqualTo(merchantId);
        assertThat(response.currency()).isEqualTo("TRY");
        assertThat(response.settlementCount()).isEqualTo(2);
        assertThat(response.totalGrossAmount()).isEqualByComparingTo("3000.00");
        assertThat(response.totalCommissionAmount()).isEqualByComparingTo("75.00");
        assertThat(response.totalNetAmount()).isEqualByComparingTo("2925.00");
    }

    private PaymentEvent createPaymentEvent(String eventType, BigDecimal amount) {
        return new PaymentEvent(
                UUID.randomUUID(),
                eventType,
                UUID.randomUUID(),
                UUID.randomUUID(),
                amount,
                "TRY",
                "ORDER-SETTLEMENT-1001",
                "AUTHORIZED",
                "MOCK_BANK",
                "PROVIDER-REF-1001",
                "00",
                "APPROVED",
                LocalDateTime.now()
        );
    }

    private Settlement createSettlement(UUID paymentId, UUID merchantId, BigDecimal grossAmount) {
        BigDecimal commissionRate = new BigDecimal("0.025");
        BigDecimal commissionAmount = grossAmount.multiply(commissionRate).setScale(2);
        BigDecimal netAmount = grossAmount.subtract(commissionAmount).setScale(2);
        LocalDateTime now = LocalDateTime.now();

        return Settlement.builder()
                .id(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .paymentId(paymentId)
                .merchantId(merchantId)
                .grossAmount(grossAmount.setScale(2))
                .commissionRate(commissionRate)
                .commissionAmount(commissionAmount)
                .netAmount(netAmount)
                .currency("TRY")
                .status(SettlementStatus.CALCULATED)
                .settlementDate(LocalDate.now())
                .orderId("ORDER-SETTLEMENT-1001")
                .sourceEventType("PAYMENT_AUTHORIZED")
                .sourceEventOccurredAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}