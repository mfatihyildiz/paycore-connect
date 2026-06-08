package com.paycore.payment.provider;

import com.paycore.payment.domain.PaymentProviderType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class PaymentProviderFactory {

    private final Map<PaymentProviderType, PaymentProviderClient> clients = new EnumMap<>(PaymentProviderType.class);

    public PaymentProviderFactory(List<PaymentProviderClient> paymentProviderClients) {
        paymentProviderClients.forEach(client -> clients.put(client.getProviderType(), client));
    }

    public PaymentProviderClient getClient(PaymentProviderType providerType) {
        PaymentProviderClient client = clients.get(providerType);

        if (client == null) {
            throw new IllegalArgumentException("Unsupported payment provider type: " + providerType);
        }

        return client;
    }
}