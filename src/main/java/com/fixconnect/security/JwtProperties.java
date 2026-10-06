package com.fixconnect.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fixconnect.jwt")
public record JwtProperties(String secret, long expirationMinutes) {
}
