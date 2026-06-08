package com.paycore.gateway.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
public class RequestLoggingFilter implements Filter {

    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain
    ) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        System.out.println(
                "[API-GATEWAY] "
                        + LocalDateTime.now()
                        + " "
                        + httpRequest.getMethod()
                        + " "
                        + httpRequest.getRequestURI()
        );

        chain.doFilter(request, response);
    }
}