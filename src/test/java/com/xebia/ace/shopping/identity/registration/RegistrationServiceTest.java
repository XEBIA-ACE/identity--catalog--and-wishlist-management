package com.xebia.ace.shopping.identity.registration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    private static final RequestContext SECURE = new RequestContext(true, "corr-1");
    private static final RegistrationRequest VALID = new RegistrationRequest(" jane@example.com ", "+1 415 555 2671", "web");

    @Mock
    private UserRepository userRepository;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private RegistrationAuditLogger auditLogger;

    private RegistrationService service;

    @BeforeEach
    void setUp() {
        service = new RegistrationService(new RegistrationValidator(), userRepository, transactionTemplate, auditLogger,
                new RegistrationProperties(true));
    }

    @SuppressWarnings("unchecked")
    private void runTransactionsInline() {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> ((TransactionCallback<Object>) inv.getArgument(0)).doInTransaction(null));
    }

    @Test
    void createsUserWithNormalizedAttributesAndGeneratedId() {
        runTransactionsInline();

        RegistrationOutcome outcome = service.register(VALID, SECURE);

        ArgumentCaptor<NewUser> captor = ArgumentCaptor.forClass(NewUser.class);
        verify(userRepository).insert(captor.capture());
        NewUser saved = captor.getValue();
        assertThat(saved.email()).isEqualTo("jane@example.com");
        assertThat(saved.mobile()).isEqualTo("+14155552671");
        assertThat(saved.status()).isEqualTo(NewUser.STATUS_ACTIVE);
        assertThat(saved.clientContext()).isEqualTo("web");

        assertThat(outcome.status()).isEqualTo(RegistrationOutcome.Status.CREATED);
        assertThat(outcome.result().outcome()).isEqualTo(RegistrationResult.SUCCESS);
        assertThat(outcome.result().userId()).isEqualTo(saved.userId());
        assertThat(outcome.result().message()).isEqualTo("Account created.");
        assertThat(outcome.result().fieldErrors()).isNull();
        verify(auditLogger).logSuccess(SECURE, saved.userId(), "jane@example.com", "+14155552671");
    }

    @Test
    void rejectsInsecureTransportWithoutTouchingPersistence() {
        RegistrationOutcome outcome = service.register(VALID, new RequestContext(false, "corr-2"));

        assertThat(outcome.status()).isEqualTo(RegistrationOutcome.Status.INSECURE_TRANSPORT);
        assertThat(outcome.result().message()).isEqualTo("Secure connection required.");
        verifyNoInteractions(userRepository, transactionTemplate);
    }

    @Test
    void allowsPlainHttpWhenSecureTransportNotRequired() {
        service = new RegistrationService(new RegistrationValidator(), userRepository, transactionTemplate, auditLogger,
                new RegistrationProperties(false));
        runTransactionsInline();

        assertThat(service.register(VALID, new RequestContext(false, "c")).status())
                .isEqualTo(RegistrationOutcome.Status.CREATED);
    }

    @Test
    void returnsFieldErrorsForMissingFieldsWithoutTouchingPersistence() {
        RegistrationOutcome outcome = service.register(new RegistrationRequest("", null, null), SECURE);

        assertThat(outcome.status()).isEqualTo(RegistrationOutcome.Status.INVALID);
        assertThat(outcome.result().fieldErrors()).containsOnlyKeys("email", "mobile");
        assertThat(outcome.result().userId()).isNull();
        verifyNoInteractions(userRepository, transactionTemplate);
        verify(auditLogger).logRejected(eq(SECURE), eq(RegistrationAuditLogger.ERROR_VALIDATION), any(), anyString(), anyString());
    }

    @Test
    void reportsEmailAndMobileConflictsFromPreCheck() {
        runTransactionsInline();
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(true);
        when(userRepository.existsByMobile("+14155552671")).thenReturn(true);

        RegistrationOutcome outcome = service.register(VALID, SECURE);

        assertThat(outcome.status()).isEqualTo(RegistrationOutcome.Status.INVALID);
        assertThat(outcome.result().fieldErrors())
                .containsEntry("email", RegistrationService.EMAIL_TAKEN)
                .containsEntry("mobile", RegistrationService.MOBILE_TAKEN);
        verify(userRepository, never()).insert(any());
    }

    @Test
    void mapsUniqueConstraintRaceToFieldConflict() {
        runTransactionsInline();
        doThrow(new DuplicateUserException("mobile", new DuplicateKeyException("uk_users_mobile")))
                .when(userRepository).insert(any());

        RegistrationOutcome outcome = service.register(VALID, SECURE);

        assertThat(outcome.status()).isEqualTo(RegistrationOutcome.Status.INVALID);
        assertThat(outcome.result().fieldErrors()).containsOnlyKeys("mobile");
        verify(auditLogger).logRejected(eq(SECURE), eq(RegistrationAuditLogger.ERROR_DUPLICATE), any(), anyString(), anyString());
    }

    @Test
    void returnsFriendlyUnavailableWhenDatabaseIsDown() {
        when(transactionTemplate.execute(any())).thenThrow(new CannotGetJdbcConnectionException("connection refused"));

        RegistrationOutcome outcome = service.register(VALID, SECURE);

        assertThat(outcome.status()).isEqualTo(RegistrationOutcome.Status.UNAVAILABLE);
        assertThat(outcome.result().message()).isEqualTo("We can't create your account right now. Please try again.");
        assertThat(outcome.result().userId()).isNull();
        verify(auditLogger).logFailure(eq(SECURE), eq(RegistrationAuditLogger.ERROR_DEPENDENCY),
                any(CannotGetJdbcConnectionException.class), eq("jane@example.com"), eq("+14155552671"));
    }

    @Test
    void returnsFriendlyUnavailableOnUnexpectedError() {
        runTransactionsInline();
        doThrow(new CannotAcquireLockException("lock timeout")).when(userRepository).insert(any());

        RegistrationOutcome outcome = service.register(VALID, SECURE);

        assertThat(outcome.status()).isEqualTo(RegistrationOutcome.Status.UNAVAILABLE);
        verify(auditLogger, never()).logSuccess(any(), any(), any(), any());
    }
}
