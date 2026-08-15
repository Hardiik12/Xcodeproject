package com.communityott.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH_SCHEME = "BearerAuth";
    public static final String DEV_USER_ID_SCHEME = "DevUserIdAuth";

    @Bean
    public OpenAPI communityOttOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CommunityOTT Monolithic Backend API")
                        .description("REST API specifications for CommunityOTT platform — OTT, Documentaries, Culture & Podcasts.")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("CommunityOTT Engineering Team")
                                .email("engineering@communityott.org"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://communityott.org")))
                .addSecurityItem(new SecurityRequirement()
                        .addList(BEARER_AUTH_SCHEME)
                        .addList(DEV_USER_ID_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH_SCHEME, new SecurityScheme()
                                .name("Authorization")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT Access Token Authentication. Format: Bearer <JWT>"))
                        .addSecuritySchemes(DEV_USER_ID_SCHEME, new SecurityScheme()
                                .name("X-Dev-User-Id")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("Development User ID Header for local testing (e.g. 1 for SUPER_ADMIN)")));
    }
}
