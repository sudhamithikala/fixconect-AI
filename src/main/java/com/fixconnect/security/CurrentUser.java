package com.fixconnect.security;

import com.fixconnect.common.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Resolves the id of the authenticated user from the JWT subject. */
@Component
public class CurrentUser {

    public Long id() {
        return optionalId().orElseThrow(() -> ApiException.unauthorized("Authentication required"));
    }

    public Optional<Long> optionalId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            try {
                return Optional.of(Long.valueOf(jwt.getSubject()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
