package com.paycore.merchant.repository;

import com.paycore.merchant.domain.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    boolean existsByEmail(String email);

    boolean existsByApiKey(String apiKey);

    Optional<Merchant> findByApiKey(String apiKey);
}