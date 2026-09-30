package com.xebia.ace.shopping.identity.registration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class UserRegistrationIT {

    private static final String ENDPOINT = "/api/v1/users/register";
    private static final String UUID_PATTERN = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcClient jdbcClient;
    @SpyBean
    private UserRepository userRepository;

    @BeforeEach
    void cleanUsers() {
        jdbcClient.sql("DELETE FROM users").update();
    }

    private static MockHttpServletRequestBuilder register(String json) {
        return post(ENDPOINT).secure(true).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private static String body(String email, String mobile) {
        return "{\"email\":\"" + email + "\",\"mobile\":\"" + mobile + "\",\"client_context\":\"web\"}";
    }

    private int userCount() {
        return jdbcClient.sql("SELECT count(*) FROM users").query(Integer.class).single();
    }

    @Test
    void t1_validRegistrationCreatesUserWithImmutableIdAndReturns201() throws Exception {
        MvcResult result = mockMvc.perform(register(body("jane@example.com", "+14155552671")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("SUCCESS"))
                .andExpect(jsonPath("$.user_id").value(matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.message").value("Account created."))
                .andExpect(jsonPath("$.field_errors").doesNotExist())
                .andReturn();

        String userId = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.user_id");
        Map<String, Object> row = jdbcClient.sql("SELECT * FROM users WHERE user_id = :id")
                .param("id", UUID.fromString(userId)).query().singleRow();
        assertThat(row).containsEntry("email", "jane@example.com")
                .containsEntry("mobile", "+14155552671")
                .containsEntry("status", "ACTIVE")
                .containsEntry("client_context", "web");
        assertThat(row.get("created_at")).isNotNull();
    }

    @Test
    void t2_missingEmailReturns422AndPersistsNothing() throws Exception {
        mockMvc.perform(register("{\"mobile\":\"+14155552671\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.outcome").value("FAILURE"))
                .andExpect(jsonPath("$.user_id").isEmpty())
                .andExpect(jsonPath("$.message").value("Please correct the highlighted fields."))
                .andExpect(jsonPath("$.field_errors.email").value("Email is required."))
                .andExpect(jsonPath("$.field_errors.mobile").doesNotExist());
        assertThat(userCount()).isZero();
    }

    @Test
    void t3_missingMobileReturns422AndPersistsNothing() throws Exception {
        mockMvc.perform(register("{\"email\":\"jane@example.com\",\"mobile\":\"  \"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.field_errors.mobile").value("Mobile number is required."));
        assertThat(userCount()).isZero();
    }

    @Test
    void t4_invalidEmailReturns422() throws Exception {
        mockMvc.perform(register(body("not-an-email", "+14155552671")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.field_errors.email").value("Enter a valid email address."));
        assertThat(userCount()).isZero();
    }

    @Test
    void t5_invalidMobileReturns422() throws Exception {
        mockMvc.perform(register(body("jane@example.com", "12ab")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.field_errors.mobile").exists());
        assertThat(userCount()).isZero();
    }

    @Test
    void t6_existingEmailCaseInsensitiveReturns422Conflict() throws Exception {
        mockMvc.perform(register(body("jane@example.com", "+14155552671"))).andExpect(status().isCreated());

        mockMvc.perform(register(body("JANE@Example.com", "+14155550000")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.field_errors.email").value("An account with this email already exists."))
                .andExpect(jsonPath("$.field_errors.mobile").doesNotExist());
        assertThat(userCount()).isEqualTo(1);
    }

    @Test
    void t7_existingMobileReturns422Conflict() throws Exception {
        mockMvc.perform(register(body("jane@example.com", "+14155552671"))).andExpect(status().isCreated());

        mockMvc.perform(register(body("john@example.com", "+1 (415) 555-2671")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.field_errors.mobile").value("An account with this mobile number already exists."));
        assertThat(userCount()).isEqualTo(1);
    }

    @Test
    void t8_nonHttpsRequestRejectedWith400() throws Exception {
        mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(body("jane@example.com", "+14155552671")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.outcome").value("FAILURE"))
                .andExpect(jsonPath("$.message").value("Secure connection required."));
        assertThat(userCount()).isZero();
    }

    @Test
    void t8_httpsTerminatedAtGatewayIsAcceptedViaForwardedProto() throws Exception {
        mockMvc.perform(post(ENDPOINT).header("X-Forwarded-Proto", "https")
                        .contentType(MediaType.APPLICATION_JSON).content(body("jane@example.com", "+14155552671")))
                .andExpect(status().isCreated());
        assertThat(userCount()).isEqualTo(1);
    }

    @Test
    void t9_databaseOutageReturns503FriendlyMessageAndLogsDiagnostics(CapturedOutput output) throws Exception {
        doThrow(new CannotGetJdbcConnectionException("Connection refused")).when(userRepository).existsByEmail(any());

        mockMvc.perform(register(body("jane@example.com", "+14155552671")).header("X-Correlation-Id", "corr-t9"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("X-Correlation-Id", "corr-t9"))
                .andExpect(jsonPath("$.outcome").value("FAILURE"))
                .andExpect(jsonPath("$.user_id").isEmpty())
                .andExpect(jsonPath("$.message").value("We can't create your account right now. Please try again."))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Connection refused"))));

        assertThat(userCount()).isZero();
        assertThat(output.getOut())
                .contains("event=registration_failed", "correlation_id=corr-t9", "error_code=REG_DEPENDENCY_FAILURE", "error_id=")
                .doesNotContain("jane@example.com", "+14155552671");
    }

    @Test
    void t9_failureAfterInsertRollsBackSoNoPartialUserRemains() throws Exception {
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new QueryTimeoutException("statement timeout");
        }).when(userRepository).insert(any());

        mockMvc.perform(register(body("jane@example.com", "+14155552671")))
                .andExpect(status().isServiceUnavailable());
        assertThat(userCount()).isZero();
    }

    @Test
    void t10_concurrentDuplicateRegistrationsCreateExactlyOneUser() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Integer>> calls = List.of(1, 2).stream()
                .map(i -> CompletableFuture.supplyAsync(() -> {
                    try {
                        start.await(5, TimeUnit.SECONDS);
                        return mockMvc.perform(register(body("race@example.com", "+1415555000" + i)))
                                .andReturn().getResponse().getStatus();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }))
                .toList();
        start.countDown();

        List<Integer> statuses = calls.stream().map(CompletableFuture::join).sorted().toList();
        assertThat(statuses).containsExactly(201, 422);
        assertThat(userCount()).isEqualTo(1);
    }

    @Test
    void malformedJsonReturns400WithoutPersisting() throws Exception {
        mockMvc.perform(register("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.outcome").value("FAILURE"));
        assertThat(userCount()).isZero();
    }

    @Test
    void successLogsMaskPii(CapturedOutput output) throws Exception {
        mockMvc.perform(register(body("jane.doe@example.com", "+14155552671"))).andExpect(status().isCreated());

        assertThat(output.getOut())
                .contains("event=registration_succeeded", "j***@example.com", "***71")
                .doesNotContain("jane.doe@example.com", "+14155552671");
    }

    @Test
    void registrationScreenIsServedWithMandatoryFields() throws Exception {
        mockMvc.perform(get("/").secure(true))
                .andExpect(forwardedUrl("index.html"));
        mockMvc.perform(get("/index.html").secure(true))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"email\"")))
                .andExpect(content().string(containsString("id=\"mobile\"")))
                .andExpect(content().string(containsString("(required)")));
    }
}
