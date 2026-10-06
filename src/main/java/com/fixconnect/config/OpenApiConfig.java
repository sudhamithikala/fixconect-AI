package com.fixconnect.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** Swagger UI: http://localhost:8080/swagger-ui.html */
@Configuration
public class OpenApiConfig {

    public static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI fixConnectOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FixConnect AI - REST API")
                        .version("1.0.0")
                        .description("""
                                Backend for the FixConnect AI home-repair marketplace (customers + service providers).

                                **How to try it**
                                1. `POST /api/auth/login` with a demo account - customer: `customer@gmail.com` / `12345`, \
                                provider: `provider@gmail.com` / `12345`, admin: `adminfixconnectai@gmail.com` / `Admin@123` (role `ADMIN`).
                                2. Copy the `token` from the response, click **Authorize** and paste it.
                                3. Customer endpoints live under `/api/customer/**`, provider endpoints under `/api/provider/**`.
                                """)
                        .contact(new Contact().name("FixConnect AI").email(com.fixconnect.service.EmailService.SUPPORT_EMAIL)))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT returned by /api/auth/login, /api/auth/register/* or /api/auth/google")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .tags(List.of(
                        new Tag().name("01. Auth").description("Login, registration, Google sign-in, password reset (login.html, signup.html, *-register.html, forgot-password.html)"),
                        new Tag().name("02. Public Catalog").description("Service categories, emergency categories, contact form (index.html, service.html, contact.html, emergency.html)"),
                        new Tag().name("03. Technicians").description("Browse / view verified technicians (customer dashboard: Find & Book Technicians)"),
                        new Tag().name("04. AI").description("AI problem analysis and AI repair guide"),
                        new Tag().name("05. Customer - Profile").description("Profile, saved addresses, settings, dashboard (customer-profile.html)"),
                        new Tag().name("06. Customer - Requests & Bookings").description("Request service, complaints, bookings, tracking, ETA, photos, history, reviews"),
                        new Tag().name("07. Customer - Emergency SOS").description("Priority SOS dispatch"),
                        new Tag().name("08. Customer - Favourites, Invoices, Warranty, Rewards").description("Favourite technicians, invoices & payment, warranty claims, loyalty rewards"),
                        new Tag().name("09. Provider - Profile & Settings").description("Technician profile, verification, availability, services & rates, payout settings (provider-profile.html)"),
                        new Tag().name("10. Provider - Requests & Jobs").description("Incoming requests, accept/decline, schedule, status/ETA updates, photos, invoices"),
                        new Tag().name("11. Provider - Insights").description("Dashboard, customers, reviews, warranties, performance analytics"),
                        new Tag().name("12. Notifications").description("Notifications for both roles"),
                        new Tag().name("13. Account & Files").description("Password change, current user, uploaded files, invoice PDF"),
                        new Tag().name("14. Admin").description("Admin console: customers, service providers, verification, deactivate / reactivate (admin-login.html)"),
                        new Tag().name("00. Health").description("Service health")));
    }

    @Bean
    public GroupedOpenApi allApis() {
        return GroupedOpenApi.builder().group("1-all").pathsToMatch("/**").build();
    }

    @Bean
    public GroupedOpenApi adminApis() {
        return GroupedOpenApi.builder().group("4-admin")
                .pathsToMatch("/api/admin/**", "/api/auth/**", "/api/users/**")
                .build();
    }

    @Bean
    public GroupedOpenApi customerApis() {
        return GroupedOpenApi.builder().group("2-customer")
                .pathsToMatch("/api/customer/**", "/api/auth/**", "/api/technicians/**", "/api/services/**", "/api/ai/diagnose",
                        "/api/notifications/**", "/api/invoices/**", "/api/users/**")
                .build();
    }

    @Bean
    public GroupedOpenApi providerApis() {
        return GroupedOpenApi.builder().group("3-provider")
                .pathsToMatch("/api/provider/**", "/api/auth/**", "/api/ai/**", "/api/notifications/**", "/api/invoices/**", "/api/users/**")
                .build();
    }
}
