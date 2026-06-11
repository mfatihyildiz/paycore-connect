package com.paycore.fraud.service;

import com.paycore.fraud.domain.FraudCheck;
import com.paycore.fraud.domain.FraudDecision;
import com.paycore.fraud.domain.FraudRiskLevel;
import com.paycore.fraud.dto.FraudCheckRequest;
import com.paycore.fraud.dto.FraudCheckResponse;
import com.paycore.fraud.repository.FraudCheckRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudServiceTest {

    @Mock
    private FraudCheckRepository fraudCheckRepository;

    private FraudService fraudService;

    @BeforeEach
    void setUp() {
        fraudService = new FraudService(fraudCheckRepository);

        ReflectionTestUtils.setField(
                fraudService,
                "highAmountThreshold",
                new BigDecimal("50000")
        );

        ReflectionTestUtils.setField(
                fraudService,
                "cardAttemptThreshold",
                3L
        );

        ReflectionTestUtils.setField(
                fraudService,
                "ipAttemptThreshold",
                5L
        );
    }

    @Test
    void checkPaymentRisk_shouldApprovePayment_whenAmountIsLowAndVelocityLimitsAreNotExceeded() {
        FraudCheckRequest request = createRequest(
                new BigDecimal("1000.00"),
                "ORDER-FRAUD-LOW-1001",
                "card_token_low_risk",
                "192.168.1.10"
        );

        when(fraudCheckRepository.countByCardTokenAndCheckedAtAfter(
                eq(request.cardToken()),
                any(LocalDateTime.class)
        )).thenReturn(0L);

        when(fraudCheckRepository.countByIpAddressAndCheckedAtAfter(
                eq(request.ipAddress()),
                any(LocalDateTime.class)
        )).thenReturn(0L);

        when(fraudCheckRepository.save(any(FraudCheck.class)))
                .thenAnswer(invocation -> {
                    FraudCheck fraudCheck = invocation.getArgument(0);
                    fraudCheck.setId("fraud-check-1");
                    return fraudCheck;
                });

        FraudCheckResponse response = fraudService.checkPaymentRisk(request);

        assertThat(response).isNotNull();
        assertThat(response.riskScore()).isZero();
        assertThat(response.riskLevel()).isEqualTo(FraudRiskLevel.LOW);
        assertThat(response.decision()).isEqualTo(FraudDecision.APPROVED);
        assertThat(response.triggeredRules()).isEmpty();
        assertThat(response.message()).isEqualTo("Payment risk is acceptable");

        verify(fraudCheckRepository).save(any(FraudCheck.class));
    }

    @Test
    void checkPaymentRisk_shouldReturnReview_whenAmountIsHigherThanThreshold() {
        FraudCheckRequest request = createRequest(
                new BigDecimal("75000.00"),
                "ORDER-FRAUD-HIGH-AMOUNT-1001",
                "card_token_high_amount",
                "192.168.1.20"
        );

        when(fraudCheckRepository.countByCardTokenAndCheckedAtAfter(
                eq(request.cardToken()),
                any(LocalDateTime.class)
        )).thenReturn(0L);

        when(fraudCheckRepository.countByIpAddressAndCheckedAtAfter(
                eq(request.ipAddress()),
                any(LocalDateTime.class)
        )).thenReturn(0L);

        when(fraudCheckRepository.save(any(FraudCheck.class)))
                .thenAnswer(invocation -> {
                    FraudCheck fraudCheck = invocation.getArgument(0);
                    fraudCheck.setId("fraud-check-2");
                    return fraudCheck;
                });

        FraudCheckResponse response = fraudService.checkPaymentRisk(request);

        assertThat(response).isNotNull();
        assertThat(response.riskScore()).isEqualTo(50);
        assertThat(response.riskLevel()).isEqualTo(FraudRiskLevel.MEDIUM);
        assertThat(response.decision()).isEqualTo(FraudDecision.REVIEW);
        assertThat(response.triggeredRules()).containsExactly("HIGH_AMOUNT");
        assertThat(response.message()).isEqualTo("Payment requires additional review but can continue");
    }

    @Test
    void checkPaymentRisk_shouldReturnReview_whenCardVelocityLimitIsReached() {
        FraudCheckRequest request = createRequest(
                new BigDecimal("1000.00"),
                "ORDER-FRAUD-CARD-VELOCITY-1001",
                "card_token_velocity",
                "192.168.1.30"
        );

        when(fraudCheckRepository.countByCardTokenAndCheckedAtAfter(
                eq(request.cardToken()),
                any(LocalDateTime.class)
        )).thenReturn(3L);

        when(fraudCheckRepository.countByIpAddressAndCheckedAtAfter(
                eq(request.ipAddress()),
                any(LocalDateTime.class)
        )).thenReturn(0L);

        when(fraudCheckRepository.save(any(FraudCheck.class)))
                .thenAnswer(invocation -> {
                    FraudCheck fraudCheck = invocation.getArgument(0);
                    fraudCheck.setId("fraud-check-3");
                    return fraudCheck;
                });

        FraudCheckResponse response = fraudService.checkPaymentRisk(request);

        assertThat(response).isNotNull();
        assertThat(response.riskScore()).isEqualTo(30);
        assertThat(response.riskLevel()).isEqualTo(FraudRiskLevel.MEDIUM);
        assertThat(response.decision()).isEqualTo(FraudDecision.REVIEW);
        assertThat(response.triggeredRules()).containsExactly("CARD_VELOCITY_LIMIT");
    }

    @Test
    void checkPaymentRisk_shouldIncreaseRiskScore_whenIpVelocityLimitIsReached() {
        FraudCheckRequest request = createRequest(
                new BigDecimal("1000.00"),
                "ORDER-FRAUD-IP-VELOCITY-1001",
                "card_token_ip_velocity",
                "192.168.1.40"
        );

        when(fraudCheckRepository.countByCardTokenAndCheckedAtAfter(
                eq(request.cardToken()),
                any(LocalDateTime.class)
        )).thenReturn(0L);

        when(fraudCheckRepository.countByIpAddressAndCheckedAtAfter(
                eq(request.ipAddress()),
                any(LocalDateTime.class)
        )).thenReturn(5L);

        when(fraudCheckRepository.save(any(FraudCheck.class)))
                .thenAnswer(invocation -> {
                    FraudCheck fraudCheck = invocation.getArgument(0);
                    fraudCheck.setId("fraud-check-4");
                    return fraudCheck;
                });

        FraudCheckResponse response = fraudService.checkPaymentRisk(request);

        assertThat(response).isNotNull();
        assertThat(response.riskScore()).isEqualTo(20);
        assertThat(response.riskLevel()).isEqualTo(FraudRiskLevel.LOW);
        assertThat(response.decision()).isEqualTo(FraudDecision.APPROVED);
        assertThat(response.triggeredRules()).containsExactly("IP_VELOCITY_LIMIT");
    }

    @Test
    void checkPaymentRisk_shouldRejectPayment_whenCombinedRiskScoreIsHigh() {
        FraudCheckRequest request = createRequest(
                new BigDecimal("90000.00"),
                "ORDER-FRAUD-REJECTED-1001",
                "card_token_rejected",
                "192.168.1.50"
        );

        when(fraudCheckRepository.countByCardTokenAndCheckedAtAfter(
                eq(request.cardToken()),
                any(LocalDateTime.class)
        )).thenReturn(3L);

        when(fraudCheckRepository.countByIpAddressAndCheckedAtAfter(
                eq(request.ipAddress()),
                any(LocalDateTime.class)
        )).thenReturn(5L);

        when(fraudCheckRepository.save(any(FraudCheck.class)))
                .thenAnswer(invocation -> {
                    FraudCheck fraudCheck = invocation.getArgument(0);
                    fraudCheck.setId("fraud-check-5");
                    return fraudCheck;
                });

        FraudCheckResponse response = fraudService.checkPaymentRisk(request);

        assertThat(response).isNotNull();
        assertThat(response.riskScore()).isEqualTo(100);
        assertThat(response.riskLevel()).isEqualTo(FraudRiskLevel.HIGH);
        assertThat(response.decision()).isEqualTo(FraudDecision.REJECTED);
        assertThat(response.triggeredRules()).containsExactly(
                "HIGH_AMOUNT",
                "CARD_VELOCITY_LIMIT",
                "IP_VELOCITY_LIMIT"
        );
        assertThat(response.message()).isEqualTo("Payment rejected due to high fraud risk");
    }

    @Test
    void checkPaymentRisk_shouldNotCheckIpVelocity_whenIpAddressIsBlank() {
        FraudCheckRequest request = createRequest(
                new BigDecimal("1000.00"),
                "ORDER-FRAUD-BLANK-IP-1001",
                "card_token_blank_ip",
                " "
        );

        when(fraudCheckRepository.countByCardTokenAndCheckedAtAfter(
                eq(request.cardToken()),
                any(LocalDateTime.class)
        )).thenReturn(0L);

        when(fraudCheckRepository.save(any(FraudCheck.class)))
                .thenAnswer(invocation -> {
                    FraudCheck fraudCheck = invocation.getArgument(0);
                    fraudCheck.setId("fraud-check-6");
                    return fraudCheck;
                });

        FraudCheckResponse response = fraudService.checkPaymentRisk(request);

        assertThat(response).isNotNull();
        assertThat(response.riskScore()).isZero();
        assertThat(response.riskLevel()).isEqualTo(FraudRiskLevel.LOW);
        assertThat(response.decision()).isEqualTo(FraudDecision.APPROVED);

        verify(fraudCheckRepository, never()).countByIpAddressAndCheckedAtAfter(
                anyString(),
                any(LocalDateTime.class)
        );
    }

    @Test
    void checkPaymentRisk_shouldPersistFraudCheckWithExpectedFields() {
        FraudCheckRequest request = createRequest(
                new BigDecimal("75000.00"),
                "ORDER-FRAUD-PERSIST-1001",
                "card_token_persist",
                "192.168.1.60"
        );

        when(fraudCheckRepository.countByCardTokenAndCheckedAtAfter(
                eq(request.cardToken()),
                any(LocalDateTime.class)
        )).thenReturn(0L);

        when(fraudCheckRepository.countByIpAddressAndCheckedAtAfter(
                eq(request.ipAddress()),
                any(LocalDateTime.class)
        )).thenReturn(0L);

        when(fraudCheckRepository.save(any(FraudCheck.class)))
                .thenAnswer(invocation -> {
                    FraudCheck fraudCheck = invocation.getArgument(0);
                    fraudCheck.setId("fraud-check-7");
                    return fraudCheck;
                });

        fraudService.checkPaymentRisk(request);

        ArgumentCaptor<FraudCheck> captor = ArgumentCaptor.forClass(FraudCheck.class);

        verify(fraudCheckRepository).save(captor.capture());

        FraudCheck savedFraudCheck = captor.getValue();

        assertThat(savedFraudCheck.getPaymentId()).isEqualTo(request.paymentId().toString());
        assertThat(savedFraudCheck.getMerchantId()).isEqualTo(request.merchantId().toString());
        assertThat(savedFraudCheck.getAmount()).isEqualByComparingTo(request.amount());
        assertThat(savedFraudCheck.getCurrency()).isEqualTo(request.currency());
        assertThat(savedFraudCheck.getOrderId()).isEqualTo(request.orderId());
        assertThat(savedFraudCheck.getCardToken()).isEqualTo(request.cardToken());
        assertThat(savedFraudCheck.getIpAddress()).isEqualTo(request.ipAddress());
        assertThat(savedFraudCheck.getRiskScore()).isEqualTo(50);
        assertThat(savedFraudCheck.getRiskLevel()).isEqualTo(FraudRiskLevel.MEDIUM);
        assertThat(savedFraudCheck.getDecision()).isEqualTo(FraudDecision.REVIEW);
        assertThat(savedFraudCheck.getTriggeredRules()).containsExactly("HIGH_AMOUNT");
        assertThat(savedFraudCheck.getCheckedAt()).isNotNull();
    }

    @Test
    void getChecksByPaymentId_shouldReturnFraudCheckResponses() {
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        FraudCheck fraudCheck = FraudCheck.builder()
                .id("fraud-check-8")
                .paymentId(paymentId.toString())
                .merchantId(merchantId.toString())
                .amount(new BigDecimal("1000.00"))
                .currency("TRY")
                .orderId("ORDER-FRAUD-GET-PAYMENT-1001")
                .cardToken("card_token_get_payment")
                .ipAddress("192.168.1.70")
                .riskScore(0)
                .riskLevel(FraudRiskLevel.LOW)
                .decision(FraudDecision.APPROVED)
                .triggeredRules(List.of())
                .checkedAt(LocalDateTime.now())
                .build();

        when(fraudCheckRepository.findByPaymentId(paymentId.toString()))
                .thenReturn(List.of(fraudCheck));

        List<FraudCheckResponse> responses = fraudService.getChecksByPaymentId(paymentId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo("fraud-check-8");
        assertThat(responses.get(0).paymentId()).isEqualTo(paymentId);
        assertThat(responses.get(0).merchantId()).isEqualTo(merchantId);
        assertThat(responses.get(0).decision()).isEqualTo(FraudDecision.APPROVED);
    }

    @Test
    void getChecksByMerchantId_shouldReturnFraudCheckResponses() {
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        FraudCheck fraudCheck = FraudCheck.builder()
                .id("fraud-check-9")
                .paymentId(paymentId.toString())
                .merchantId(merchantId.toString())
                .amount(new BigDecimal("5000.00"))
                .currency("TRY")
                .orderId("ORDER-FRAUD-GET-MERCHANT-1001")
                .cardToken("card_token_get_merchant")
                .ipAddress("192.168.1.80")
                .riskScore(30)
                .riskLevel(FraudRiskLevel.MEDIUM)
                .decision(FraudDecision.REVIEW)
                .triggeredRules(List.of("CARD_VELOCITY_LIMIT"))
                .checkedAt(LocalDateTime.now())
                .build();

        when(fraudCheckRepository.findByMerchantIdOrderByCheckedAtDesc(merchantId.toString()))
                .thenReturn(List.of(fraudCheck));

        List<FraudCheckResponse> responses = fraudService.getChecksByMerchantId(merchantId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo("fraud-check-9");
        assertThat(responses.get(0).paymentId()).isEqualTo(paymentId);
        assertThat(responses.get(0).merchantId()).isEqualTo(merchantId);
        assertThat(responses.get(0).decision()).isEqualTo(FraudDecision.REVIEW);
    }

    private FraudCheckRequest createRequest(
            BigDecimal amount,
            String orderId,
            String cardToken,
            String ipAddress
    ) {
        return new FraudCheckRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                amount,
                "TRY",
                orderId,
                cardToken,
                ipAddress
        );
    }
}