package com.xebia.ace.shopping.identity.registration;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RegistrationService {

    static final String EMAIL_TAKEN = "An account with this email already exists.";
    static final String MOBILE_TAKEN = "An account with this mobile number already exists.";

    private final RegistrationValidator validator;
    private final UserRepository userRepository;
    private final TransactionTemplate transactionTemplate;
    private final RegistrationAuditLogger auditLogger;
    private final RegistrationProperties properties;

    public RegistrationService(RegistrationValidator validator,
                               UserRepository userRepository,
                               TransactionTemplate transactionTemplate,
                               RegistrationAuditLogger auditLogger,
                               RegistrationProperties properties) {
        this.validator = validator;
        this.userRepository = userRepository;
        this.transactionTemplate = transactionTemplate;
        this.auditLogger = auditLogger;
        this.properties = properties;
    }

    public RegistrationOutcome register(RegistrationRequest request, RequestContext context) {
        String email = validator.normalizeEmail(request.email());
        String mobile = validator.normalizeMobile(request.mobile());

        if (properties.requireSecureTransport() && !context.secure()) {
            auditLogger.logRejected(context, RegistrationAuditLogger.ERROR_INSECURE_TRANSPORT, List.of(), email, mobile);
            return new RegistrationOutcome(RegistrationOutcome.Status.INSECURE_TRANSPORT,
                    RegistrationResult.insecureTransport());
        }

        Map<String, String> fieldErrors = validator.validate(request);
        if (!fieldErrors.isEmpty()) {
            auditLogger.logRejected(context, RegistrationAuditLogger.ERROR_VALIDATION, fieldErrors.keySet(), email, mobile);
            return invalid(fieldErrors);
        }

        CreationResult creation;
        try {
            creation = transactionTemplate.execute(status -> createUser(email, mobile, request.clientContext()));
        } catch (DuplicateUserException e) {
            creation = CreationResult.conflicts(conflictFor(e.field()));
        } catch (RuntimeException e) {
            auditLogger.logFailure(context, RegistrationAuditLogger.ERROR_DEPENDENCY, e, email, mobile);
            return new RegistrationOutcome(RegistrationOutcome.Status.UNAVAILABLE, RegistrationResult.unavailable());
        }

        if (creation == null || creation.userId() == null) {
            Map<String, String> conflicts = creation == null ? Map.of() : creation.conflicts();
            auditLogger.logRejected(context, RegistrationAuditLogger.ERROR_DUPLICATE, conflicts.keySet(), email, mobile);
            return invalid(conflicts);
        }

        auditLogger.logSuccess(context, creation.userId(), email, mobile);
        return new RegistrationOutcome(RegistrationOutcome.Status.CREATED, RegistrationResult.created(creation.userId()));
    }

    private CreationResult createUser(String email, String mobile, String clientContext) {
        Map<String, String> conflicts = new LinkedHashMap<>();
        if (userRepository.existsByEmail(email)) {
            conflicts.putAll(conflictFor(RegistrationValidator.EMAIL));
        }
        if (userRepository.existsByMobile(mobile)) {
            conflicts.putAll(conflictFor(RegistrationValidator.MOBILE));
        }
        if (!conflicts.isEmpty()) {
            return CreationResult.conflicts(conflicts);
        }

        UUID userId = UUID.randomUUID();
        userRepository.insert(new NewUser(userId, email, mobile, NewUser.STATUS_ACTIVE, clientContext));
        return CreationResult.created(userId);
    }

    private static Map<String, String> conflictFor(String field) {
        return RegistrationValidator.EMAIL.equals(field)
                ? Map.of(RegistrationValidator.EMAIL, EMAIL_TAKEN)
                : Map.of(RegistrationValidator.MOBILE, MOBILE_TAKEN);
    }

    private static RegistrationOutcome invalid(Map<String, String> fieldErrors) {
        return new RegistrationOutcome(RegistrationOutcome.Status.INVALID, RegistrationResult.invalid(fieldErrors));
    }

    private record CreationResult(UUID userId, Map<String, String> conflicts) {

        static CreationResult created(UUID userId) {
            return new CreationResult(userId, Map.of());
        }

        static CreationResult conflicts(Map<String, String> conflicts) {
            return new CreationResult(null, conflicts);
        }
    }
}
