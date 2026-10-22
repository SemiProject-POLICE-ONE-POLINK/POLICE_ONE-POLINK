package com.polink.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    // API 문서 정보와 JWT 입력 방식 설정
    @Bean
    public OpenAPI polinkOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("POLINK API")
                        .version("v1")
                        .description("POLINK API 문서")
                )
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_AUTH,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                        )
                )
                // 기본적으로 API 호출에 Bearer 토큰을 사용하도록 문서화
                .addSecurityItem(
                        new SecurityRequirement().addList(BEARER_AUTH)
                );
    }

    // 로그인·회원가입은 토큰 없이 호출하도록 문서 설정
    @Bean
    public OpenApiCustomizer publicAuthEndpoints() {
        return openAPI -> {
            if (openAPI.getPaths() == null) {
                return;
            }

            for (String path : List.of(
                    "/api/v1/auth/login",
                    "/api/v1/auth/signup"
            )) {
                PathItem pathItem = openAPI.getPaths().get(path);

                if (pathItem != null && pathItem.getPost() != null) {
                    pathItem.getPost().setSecurity(List.of());
                }
            }
        };
    }
}
