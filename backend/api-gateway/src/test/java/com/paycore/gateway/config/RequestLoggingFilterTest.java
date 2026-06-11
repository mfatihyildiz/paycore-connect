package com.paycore.gateway.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RequestLoggingFilterTest {

    private RequestLoggingFilter requestLoggingFilter;

    private PrintStream originalSystemOut;
    private ByteArrayOutputStream outputStream;

    @BeforeEach
    void setUp() {
        requestLoggingFilter = new RequestLoggingFilter();

        originalSystemOut = System.out;
        outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalSystemOut);
    }

    @Test
    void doFilter_shouldContinueFilterChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/api/payments/test-payment-id");

        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain filterChain = mock(FilterChain.class);

        requestLoggingFilter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void doFilter_shouldLogHttpMethodAndRequestUri() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/payments/initiate");

        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain filterChain = mock(FilterChain.class);

        requestLoggingFilter.doFilter(request, response, filterChain);

        String logOutput = outputStream.toString();

        assertThat(logOutput).contains("[API-GATEWAY]");
        assertThat(logOutput).contains("POST");
        assertThat(logOutput).contains("/api/payments/initiate");

        verify(filterChain).doFilter(request, response);
    }
}