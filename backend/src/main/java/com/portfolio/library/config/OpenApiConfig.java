package com.portfolio.library.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Metadata for the generated OpenAPI documentation, browsable at {@code /swagger-ui.html}.
 *
 * <p>springdoc scans the controllers to describe every endpoint automatically; this class only adds a
 * title and declares that the API uses "Bearer" JWT authentication. In Swagger UI click
 * <b>Authorize</b>, paste the {@code token} returned by {@code POST /api/auth/login}, and every
 * "Try it out" call will send it.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Library Management API", version = "1.0",
                description = "Catalog, loans, members and statistics for a small lending library."),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
