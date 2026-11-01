package com.lanveriq.server.security;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Security's formLogin/LogoutFilter own these POST routes, not MVC.
 * Describe their actual filter contract without creating duplicate endpoints.
 *
 * Only activated when BOTH console auth and (test-only) Springdoc are opted in.
 * Normal production configuration keeps both OFF and retains deny-all routes.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = {
        "lanveriq.console.auth.enabled",
        "springdoc.api-docs.enabled"
}, havingValue = "true")
public class ConsoleAuthenticationOpenApiConfiguration {
    @Bean
    OpenApiCustomizer springSecurityAuthenticationRoutes() {
        return document -> {
            var loginForm = new ObjectSchema()
                    .addProperty("username", new StringSchema())
                    .addProperty("password", new StringSchema().format("password"))
                    .addRequiredItem("username")
                    .addRequiredItem("password");
            var form = new RequestBody()
                    .required(true)
                    .description("Send form fields only in the HTTPS request body. Never in a URL.")
                    .content(new Content().addMediaType(
                            "application/x-www-form-urlencoded",
                            new MediaType().schema(loginForm)));
            var login = new Operation()
                    .operationId("consoleLogin")
                    .summary("Open a secure administrator browser session")
                    .description("Spring Security UsernamePasswordAuthenticationFilter; " +
                            "fetch CSRF first and submit the same-origin session cookie, " +
                            "Origin and X-CSRF-TOKEN. Response body is always empty.")
                    .requestBody(form)
                    .addParametersItem(requiredOrigin())
                    .addParametersItem(requiredCsrf())
                    .responses(new ApiResponses()
                            .addApiResponse("204", response("Login successful; no response body"))
                            .addApiResponse("401", response("Invalid credentials"))
                            .addApiResponse("403", response("CSRF or console origin rejected"))
                            .addApiResponse("429", response("Login throttled"))
                            .addApiResponse("503", response("Durable authentication audit unavailable")));
            var logout = new Operation()
                    .operationId("consoleLogout")
                    .summary("Invalidate administrator browser session")
                    .description("Spring Security LogoutFilter; obtain a FRESH CSRF token " +
                            "after login, then submit same-origin cookie, Origin and X-CSRF-TOKEN.")
                    .addSecurityItem(new SecurityRequirement().addList("consoleSession"))
                    .addParametersItem(requiredOrigin())
                    .addParametersItem(requiredCsrf())
                    .responses(new ApiResponses()
                            .addApiResponse("204", response("Session invalidated; empty response"))
                            .addApiResponse("403", response("CSRF or console origin rejected"))
                            .addApiResponse("503", response("Logout audit failed after invalidation")));
            document.path("/api/v1/auth/login", new PathItem().post(login));
            document.path("/api/v1/auth/logout", new PathItem().post(logout));
        };
    }

    private static HeaderParameter requiredOrigin() {
        return new HeaderParameter().name("Origin").required(true)
                .description("Exact configured HTTPS console origin")
                .schema(new StringSchema());
    }

    private static HeaderParameter requiredCsrf() {
        return new HeaderParameter().name("X-CSRF-TOKEN").required(true)
                .description("Token from GET /api/v1/auth/csrf in the same session")
                .schema(new StringSchema());
    }

    private static ApiResponse response(String description) {
        return new ApiResponse().description(description);
    }
}
