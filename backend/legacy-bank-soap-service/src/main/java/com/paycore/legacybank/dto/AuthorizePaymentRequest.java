package com.paycore.legacybank.dto;

import jakarta.xml.bind.annotation.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "authorizePaymentRequest", namespace = "http://paycore.com/legacy-bank")
@XmlType(name = "", propOrder = {
        "merchantId",
        "amount",
        "currency",
        "orderId",
        "cardToken"
})
public class AuthorizePaymentRequest {

    @XmlElement(namespace = "http://paycore.com/legacy-bank", required = true)
    private String merchantId;

    @XmlElement(namespace = "http://paycore.com/legacy-bank", required = true)
    private BigDecimal amount;

    @XmlElement(namespace = "http://paycore.com/legacy-bank", required = true)
    private String currency;

    @XmlElement(namespace = "http://paycore.com/legacy-bank", required = true)
    private String orderId;

    @XmlElement(namespace = "http://paycore.com/legacy-bank", required = true)
    private String cardToken;
}