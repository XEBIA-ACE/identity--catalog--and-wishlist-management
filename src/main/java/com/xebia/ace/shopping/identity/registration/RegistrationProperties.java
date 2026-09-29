package com.xebia.ace.shopping.identity.registration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("registration")
public record RegistrationProperties(@DefaultValue("true") boolean requireSecureTransport) {
}
