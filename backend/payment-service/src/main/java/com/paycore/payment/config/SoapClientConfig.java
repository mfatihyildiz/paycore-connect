package com.paycore.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;

@Configuration
public class SoapClientConfig {

    @Bean
    public Jaxb2Marshaller legacyBankMarshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setPackagesToScan("com.paycore.payment.provider.soap.dto");
        return marshaller;
    }

    @Bean
    public WebServiceTemplate legacyBankWebServiceTemplate(Jaxb2Marshaller legacyBankMarshaller) {
        WebServiceTemplate template = new WebServiceTemplate();
        template.setMarshaller(legacyBankMarshaller);
        template.setUnmarshaller(legacyBankMarshaller);
        return template;
    }
}