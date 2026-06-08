package com.paycore.fraud.repository;

import com.paycore.fraud.domain.FraudCheck;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface FraudCheckRepository extends MongoRepository<FraudCheck, String> {

    List<FraudCheck> findByPaymentId(String paymentId);

    long countByCardTokenAndCheckedAtAfter(String cardToken, LocalDateTime checkedAt);

    long countByIpAddressAndCheckedAtAfter(String ipAddress, LocalDateTime checkedAt);

    List<FraudCheck> findByMerchantIdOrderByCheckedAtDesc(String merchantId);
}