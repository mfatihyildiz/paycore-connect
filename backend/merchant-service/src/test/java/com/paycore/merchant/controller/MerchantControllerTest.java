package com.paycore.merchant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.paycore.merchant.domain.MerchantStatus;
import com.paycore.merchant.dto.ApiKeyValidationResponse;
import com.paycore.merchant.dto.CreateMerchantRequest;
import com.paycore.merchant.dto.GenerateApiKeyResponse;
import com.paycore.merchant.dto.MerchantResponse;
import com.paycore.merchant.dto.UpdateMerchantStatusRequest;
import com.paycore.merchant.exception.DuplicateMerchantEmailException;
import com.paycore.merchant.exception.GlobalExceptionHandler;
import com.paycore.merchant.exception.MerchantNotFoundException;
import com.paycore.merchant.service.MerchantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MerchantControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private MerchantService merchantService;

    @BeforeEach
    void setUp() {
        merchantService = mock(MerchantService.class);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new MerchantController(merchantService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createMerchant_shouldReturnCreatedMerchant_whenRequestIsValid() throws Exception {
        UUID merchantId = UUID.randomUUID();

        CreateMerchantRequest request = new CreateMerchantRequest(
                "Test Merchant",
                "test-merchant@example.com"
        );

        MerchantResponse response = createMerchantResponse(
                merchantId,
                "Test Merchant",
                "test-merchant@example.com",
                "pk_live_test_key",
                MerchantStatus.ACTIVE
        );

        when(merchantService.createMerchant(any(CreateMerchantRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(merchantId.toString()))
                .andExpect(jsonPath("$.name").value("Test Merchant"))
                .andExpect(jsonPath("$.email").value("test-merchant@example.com"))
                .andExpect(jsonPath("$.apiKey").value("pk_live_test_key"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        ArgumentCaptor<CreateMerchantRequest> requestCaptor =
                ArgumentCaptor.forClass(CreateMerchantRequest.class);

        verify(merchantService).createMerchant(requestCaptor.capture());

        CreateMerchantRequest capturedRequest = requestCaptor.getValue();

        assertThat(capturedRequest.name()).isEqualTo("Test Merchant");
        assertThat(capturedRequest.email()).isEqualTo("test-merchant@example.com");
    }

    @Test
    void createMerchant_shouldReturnBadRequest_whenRequestBodyIsInvalid() throws Exception {
        String invalidRequestBody = """
                {
                  "name": "",
                  "email": "invalid-email-format"
                }
                """;

        mockMvc.perform(post("/api/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.validationErrors.name").exists())
                .andExpect(jsonPath("$.validationErrors.email").exists());

        verify(merchantService, never()).createMerchant(any(CreateMerchantRequest.class));
    }

    @Test
    void createMerchant_shouldReturnConflict_whenEmailAlreadyExists() throws Exception {
        CreateMerchantRequest request = new CreateMerchantRequest(
                "Duplicate Merchant",
                "duplicate@example.com"
        );

        when(merchantService.createMerchant(any(CreateMerchantRequest.class)))
                .thenThrow(new DuplicateMerchantEmailException("duplicate@example.com"));

        mockMvc.perform(post("/api/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Merchant already exists with email: duplicate@example.com"));

        verify(merchantService).createMerchant(any(CreateMerchantRequest.class));
    }

    @Test
    void getAllMerchants_shouldReturnMerchantList() throws Exception {
        UUID firstMerchantId = UUID.randomUUID();
        UUID secondMerchantId = UUID.randomUUID();

        MerchantResponse firstMerchant = createMerchantResponse(
                firstMerchantId,
                "First Merchant",
                "first@example.com",
                "pk_live_first",
                MerchantStatus.ACTIVE
        );

        MerchantResponse secondMerchant = createMerchantResponse(
                secondMerchantId,
                "Second Merchant",
                "second@example.com",
                "pk_live_second",
                MerchantStatus.PASSIVE
        );

        when(merchantService.getAllMerchants())
                .thenReturn(List.of(firstMerchant, secondMerchant));

        mockMvc.perform(get("/api/merchants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(firstMerchantId.toString()))
                .andExpect(jsonPath("$[0].name").value("First Merchant"))
                .andExpect(jsonPath("$[0].email").value("first@example.com"))
                .andExpect(jsonPath("$[0].apiKey").value("pk_live_first"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[1].id").value(secondMerchantId.toString()))
                .andExpect(jsonPath("$[1].name").value("Second Merchant"))
                .andExpect(jsonPath("$[1].email").value("second@example.com"))
                .andExpect(jsonPath("$[1].apiKey").value("pk_live_second"))
                .andExpect(jsonPath("$[1].status").value("PASSIVE"));

        verify(merchantService).getAllMerchants();
    }

    @Test
    void getMerchantById_shouldReturnMerchant_whenMerchantExists() throws Exception {
        UUID merchantId = UUID.randomUUID();

        MerchantResponse response = createMerchantResponse(
                merchantId,
                "Test Merchant",
                "test@example.com",
                "pk_live_test",
                MerchantStatus.ACTIVE
        );

        when(merchantService.getMerchantById(merchantId))
                .thenReturn(response);

        mockMvc.perform(get("/api/merchants/{merchantId}", merchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(merchantId.toString()))
                .andExpect(jsonPath("$.name").value("Test Merchant"))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.apiKey").value("pk_live_test"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(merchantService).getMerchantById(merchantId);
    }

    @Test
    void getMerchantById_shouldReturnNotFound_whenMerchantDoesNotExist() throws Exception {
        UUID merchantId = UUID.randomUUID();

        when(merchantService.getMerchantById(merchantId))
                .thenThrow(new MerchantNotFoundException(merchantId));

        mockMvc.perform(get("/api/merchants/{merchantId}", merchantId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Merchant not found with id: " + merchantId));

        verify(merchantService).getMerchantById(merchantId);
    }

    @Test
    void updateMerchantStatus_shouldReturnUpdatedMerchant_whenRequestIsValid() throws Exception {
        UUID merchantId = UUID.randomUUID();

        UpdateMerchantStatusRequest request =
                new UpdateMerchantStatusRequest(MerchantStatus.PASSIVE);

        MerchantResponse response = createMerchantResponse(
                merchantId,
                "Test Merchant",
                "test@example.com",
                "pk_live_test",
                MerchantStatus.PASSIVE
        );

        when(merchantService.updateMerchantStatus(eq(merchantId), any(UpdateMerchantStatusRequest.class)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/merchants/{merchantId}/status", merchantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(merchantId.toString()))
                .andExpect(jsonPath("$.status").value("PASSIVE"));

        ArgumentCaptor<UpdateMerchantStatusRequest> requestCaptor =
                ArgumentCaptor.forClass(UpdateMerchantStatusRequest.class);

        verify(merchantService).updateMerchantStatus(eq(merchantId), requestCaptor.capture());

        assertThat(requestCaptor.getValue().status()).isEqualTo(MerchantStatus.PASSIVE);
    }

    @Test
    void updateMerchantStatus_shouldReturnBadRequest_whenStatusIsNull() throws Exception {
        UUID merchantId = UUID.randomUUID();

        String invalidRequestBody = """
                {
                  "status": null
                }
                """;

        mockMvc.perform(patch("/api/merchants/{merchantId}/status", merchantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.validationErrors.status").exists());

        verify(merchantService, never())
                .updateMerchantStatus(eq(merchantId), any(UpdateMerchantStatusRequest.class));
    }

    @Test
    void regenerateApiKey_shouldReturnNewApiKey() throws Exception {
        UUID merchantId = UUID.randomUUID();

        GenerateApiKeyResponse response = new GenerateApiKeyResponse(
                merchantId,
                "pk_live_new_key"
        );

        when(merchantService.regenerateApiKey(merchantId))
                .thenReturn(response);

        mockMvc.perform(post("/api/merchants/{merchantId}/api-key", merchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$.apiKey").value("pk_live_new_key"));

        verify(merchantService).regenerateApiKey(merchantId);
    }

    @Test
    void validateApiKey_shouldReturnValidationResponse_whenHeaderExists() throws Exception {
        UUID merchantId = UUID.randomUUID();
        String apiKey = "pk_live_valid_key";

        ApiKeyValidationResponse response = new ApiKeyValidationResponse(
                true,
                merchantId,
                "Test Merchant",
                MerchantStatus.ACTIVE
        );

        when(merchantService.validateApiKey(apiKey))
                .thenReturn(response);

        mockMvc.perform(get("/api/merchants/validate-api-key")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$.merchantName").value("Test Merchant"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(merchantService).validateApiKey(apiKey);
    }

    @Test
    void validateApiKey_shouldReturnBadRequest_whenHeaderIsMissing() throws Exception {
        mockMvc.perform(get("/api/merchants/validate-api-key"))
                .andExpect(status().isBadRequest());

        verify(merchantService, never()).validateApiKey(anyString());
    }

    private MerchantResponse createMerchantResponse(
            UUID id,
            String name,
            String email,
            String apiKey,
            MerchantStatus status
    ) {
        LocalDateTime now = LocalDateTime.now();

        return new MerchantResponse(
                id,
                name,
                email,
                apiKey,
                status,
                now,
                now
        );
    }
}