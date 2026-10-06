package com.fixconnect.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixconnect.common.ApiError;
import com.fixconnect.domain.User;
import com.fixconnect.repository.UserRepository;
import com.fixconnect.security.JwtProperties;
import com.fixconnect.service.AuthService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/", "/api/health", "/error",
            "/api/auth/**",
            "/api/services/**",
            "/api/emergency/categories",
            "/api/contact",
            "/api/technicians/**",
            "/api/ai/diagnose",
            "/api/files/**",
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
            "/h2-console/**",
            // static front-end pages served by FrontendConfig
            "/*.html", "/*.css", "/*.js", "/images/**", "/favicon.ico"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper,
                                                   UserRepository users) throws Exception {
        AuthenticationEntryPoint entryPoint = (req, res, ex) ->
                writeError(mapper, req, res, HttpStatus.UNAUTHORIZED, ex instanceof DisabledException
                        ? AuthService.DEACTIVATED_MESSAGE : "Missing, invalid or expired access token");
        AccessDeniedHandler deniedHandler = (req, res, ex) ->
                writeError(mapper, req, res, HttpStatus.FORBIDDEN, "This endpoint is not available for your role");

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {})
                .headers(h -> h.frameOptions(f -> f.sameOrigin())) // H2 console
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.OPTIONS, "/**")).permitAll()
                        .requestMatchers(ant(PUBLIC_ENDPOINTS)).permitAll()
                        .requestMatchers(ant("/api/admin/**")).hasRole("ADMIN")
                        .requestMatchers(ant("/api/customer/**")).hasRole("CUSTOMER")
                        .requestMatchers(ant("/api/provider/**", "/api/ai/repair-guide")).hasRole("PROVIDER")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(activeUserConverter(users)))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler));
        return http.build();
    }

    /** Explicit Ant matchers: the H2 console registers a second servlet, which makes plain String matchers ambiguous. */
    private static RequestMatcher[] ant(String... patterns) {
        return Arrays.stream(patterns).map(AntPathRequestMatcher::antMatcher).toArray(RequestMatcher[]::new);
    }

    private static void writeError(ObjectMapper mapper, HttpServletRequest req, HttpServletResponse res,
                                   HttpStatus status, String message) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(), new ApiError(false, status.value(), status.getReasonPhrase(),
                message, req.getRequestURI(), null, Instant.now()));
    }

    /**
     * Converts the JWT into an authentication and rejects tokens of accounts an admin has deactivated,
     * so deactivation takes effect immediately (not only at the next login).
     */
    private Converter<Jwt, AbstractAuthenticationToken> activeUserConverter(UserRepository users) {
        JwtAuthenticationConverter delegate = jwtAuthenticationConverter();
        return jwt -> {
            Long id;
            try {
                id = Long.valueOf(jwt.getSubject());
            } catch (NumberFormatException e) {
                throw new BadCredentialsException("Invalid token subject");
            }
            boolean active = users.findById(id).map(User::isActive).orElse(false);
            if (!active) {
                throw new DisabledException(AuthService.DEACTIVATED_MESSAGE);
            }
            return delegate.convert(jwt);
        };
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    public SecretKey jwtSecretKey(JwtProperties props) {
        byte[] bytes = props.secret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("fixconnect.jwt.secret must be at least 32 bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${fixconnect.cors.allowed-origins:*}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Arrays.stream(origins.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Content-Disposition"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
