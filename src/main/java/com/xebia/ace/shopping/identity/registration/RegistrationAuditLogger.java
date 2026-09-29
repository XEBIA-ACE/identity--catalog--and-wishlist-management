package com.xebia.ace.shopping.identity.registration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.UUID;

@Component
public class RegistrationAuditLogger {

    public static final String ERROR_INSECURE_TRANSPORT = "REG_INSECURE_TRANSPORT";
    public static final String ERROR_VALIDATION = "REG_VALIDATION_FAILED";
    public static final String ERROR_DUPLICATE = "REG_DUPLICATE_USER";
    public static final String ERROR_DEPENDENCY = "REG_DEPENDENCY_FAILURE";

    private static final Logger log = LoggerFactory.getLogger(RegistrationAuditLogger.class);

    public void logSuccess(RequestContext context, UUID userId, String email, String mobile) {
        log.info("event=registration_succeeded correlation_id={} user_id={} email={} mobile={}",
                context.correlationId(), userId, PiiMasker.maskEmail(email), PiiMasker.maskMobile(mobile));
    }

    public void logRejected(RequestContext context, String errorCode, Collection<String> fields, String email, String mobile) {
        log.warn("event=registration_rejected correlation_id={} error_code={} fields={} request_fingerprint={}",
                context.correlationId(), errorCode, fields, PiiMasker.fingerprint(email, mobile));
    }

    public String logFailure(RequestContext context, String errorCode, Throwable error, String email, String mobile) {
        String errorId = UUID.randomUUID().toString();
        log.error("event=registration_failed correlation_id={} error_id={} error_code={} exception={} request_fingerprint={}",
                context.correlationId(), errorId, errorCode, error.getClass().getName(),
                PiiMasker.fingerprint(email, mobile), error);
        return errorId;
    }
}
