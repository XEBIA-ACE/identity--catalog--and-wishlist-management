package com.xebia.ace.shopping.identity.registration;

import com.xebia.ace.shopping.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserRegistrationController {

    private final RegistrationService registrationService;

    public UserRegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping(path = "/register", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RegistrationResult> register(HttpServletRequest httpRequest, @RequestBody RegistrationRequest request) {
        RequestContext context = new RequestContext(httpRequest.isSecure(), MDC.get(CorrelationIdFilter.MDC_KEY));
        RegistrationOutcome outcome = registrationService.register(request, context);
        return ResponseEntity.status(outcome.status().httpStatus()).body(outcome.result());
    }
}
