package com.paycore.legacybank.dto;

import jakarta.xml.bind.annotation.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "authorizePaymentResponse", namespace = "http://paycore.com/legacy-bank")
@XmlType(name = "", propOrder = {
        "approved",
        "bankReferenceId",
        "responseCode",
        "responseMessage"
})
public class AuthorizePaymentResponse {

    @XmlElement(namespace = "http://paycore.com/legacy-bank", required = true)
    private boolean approved;

    @XmlElement(namespace = "http://paycore.com/legacy-bank")
    private String bankReferenceId;

    @XmlElement(namespace = "http://paycore.com/legacy-bank", required = true)
    private String responseCode;

    @XmlElement(namespace = "http://paycore.com/legacy-bank", required = true)
    private String responseMessage;
}