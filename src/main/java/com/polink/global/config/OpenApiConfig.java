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
                        .description("""
                                POLINK는 AI 기반 경찰 업무 통합 관리 시스템입니다.
                                
                                이 문서에서는 회원 인증, 사건 관리, 증거물·목격자·수사기록 관리,
                                AI 수사 가설 생성, 물품 등록·조회와 반환 신청 API를 확인할 수 있습니다.
                                
                                인증이 필요한 API는 로그인 후 발급받은 JWT를 Swagger UI의
                                Authorize 버튼에 입력해 호출하세요. 회원가입과 로그인은 인증 없이 호출할 수 있습니다.
                                
                                이 서비스는 가상 데이터를 사용하는 개발용 프로토타입입니다.
                                """)
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
