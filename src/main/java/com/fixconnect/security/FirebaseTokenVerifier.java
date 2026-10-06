package com.fixconnect.security;

import com.fixconnect.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Verifies Firebase ID tokens produced by the "Continue with Google" button (signInWithPopup in login.html).
 * Uses Google's public JWKs - no service-account file is required.
 */
@Component
public class FirebaseTokenVerifier {

    private static final String JWK_URI =
            "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";

    private final String projectId;
    private volatile NimbusJwtDecoder decoder;

    public FirebaseTokenVerifier(@Value("${fixconnect.firebase.project-id}") String projectId) {
        this.projectId = projectId;
    }

    public Jwt verify(String idToken) {
        try {
            return decoder().decode(idToken);
        } catch (JwtException e) {
            throw ApiException.unauthorized("Invalid Google / Firebase ID token: " + e.getMessage());
        }
    }

    private NimbusJwtDecoder decoder() {
        if (decoder == null) {
            synchronized (this) {
                if (decoder == null) {
                    NimbusJwtDecoder d = NimbusJwtDecoder.withJwkSetUri(JWK_URI)
                            .jwsAlgorithm(SignatureAlgorithm.RS256)
                            .build();
                    OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(projectId)
                            ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Wrong audience", null));
                    d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                            JwtValidators.createDefaultWithIssuer("https://securetoken.google.com/" + projectId),
                            audience));
                    decoder = d;
                }
            }
        }
        return decoder;
    }
}
