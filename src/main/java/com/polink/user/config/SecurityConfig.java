package com.polink.user.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Configuration
public class SecurityConfig {

@Bean  
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper
    ) throws Exception {

        AuthenticationEntryPoint authenticationEntryPoint =
                (request, response, authenticationException) ->
                        writeErrorResponse(
                                response,
                                objectMapper,
                                HttpStatus.UNAUTHORIZED,
                                "로그인이 필요합니다."
                        );

        AccessDeniedHandler accessDeniedHandler =
                (request, response, accessDeniedException) ->
                        writeErrorResponse(
                                response,
                                objectMapper,
                                HttpStatus.FORBIDDEN,
                                "접근 권한이 없습니다."
                        );

        http
                // 현재는 세션 로그인 사용 X
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 세션·쿠키 인증은 비활성화
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())

                // Spring Security의 기본 로그인 창과 Basic 인증은 사용 X
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )

                // JWT 검증을 연결하기 전까지 모든 경로 허용(임시)
                .authorizeHttpRequests(authorize -> authorize

                        // 서버 내부 오류 응답이 권한 오류로 가려지지 않도록 허용
                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()

                        // ==================== 인증 ====================

                        // 회원가입
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/signup"
                        )
                        .permitAll()

                        // 로그인
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/login"
                        )
                        .permitAll()

                        // ==================== 회원·마이페이지 ====================

                        // 본인 정보 조회
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/users/me"
                        )
                        .hasAnyAuthority("CITIZEN", "POLICE", "ADMIN")

                        // 시민 마이페이지
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/users/me/mypage"
                        )
                        .hasAuthority("CITIZEN")

                        // 경찰 대시보드
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/users/me/police-dashboard"
                        )
                        .hasAuthority("POLICE")

                        // 관리자 대시보드
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/users/me/admin-dashboard"
                        )
                        .hasAuthority("ADMIN")

                        // 배정 가능한 경찰관 목록 조회
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/users/police-officers"
                        )
                        .hasAuthority("ADMIN")

                        // ==================== 수사보드 ====================

                        // 사건별 수사보드 조회
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/cases/*/investigation-board"
                        )
                        .hasAuthority("POLICE")

                        // ==================== 증거물 ====================

                        // 증거물 등록 — JSON·multipart 요청 모두 해당
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/cases/*/evidences"
                        )
                        .hasAuthority("POLICE")

                        // 사건별 증거물 목록 조회
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/cases/*/evidences"
                        )
                        .hasAnyAuthority("POLICE", "ADMIN")

                        // ==================== 목격자·진술 ====================

                        // 목격자와 최초 진술 등록
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/cases/*/witnesses"
                        )
                        .hasAuthority("POLICE")

                        // 사건별 목격자·진술 이력 조회
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/cases/*/witnesses"
                        )
                        .hasAnyAuthority("POLICE", "ADMIN")

                        // 기존 목격자의 추가·정정·번복 진술 등록
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/cases/*/witnesses/*/statements"
                        )
                        .hasAuthority("POLICE")

                        // ==================== 수사기록 ====================

                        // 수사기록 등록
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/cases/*/investigations"
                        )
                        .hasAuthority("POLICE")

                        // 수사기록 목록 조회
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/cases/*/investigations"
                        )
                        .hasAnyAuthority("POLICE", "ADMIN")

                        // ==================== 수사 가설 ====================

                        // AI 가설 후보 생성
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/cases/*/hypotheses/generate"
                        )
                        .hasAuthority("POLICE")

                        // 선택한 가설 채택·저장
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/cases/*/hypotheses"
                        )
                        .hasAuthority("POLICE")

                        // 저장된 가설 목록 조회
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/cases/*/hypotheses"
                        )
                        .hasAuthority("POLICE")

                        // 수사 가설 체크리스트 조회
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/cases/*/hypotheses/*/checklist"
                        )
                        .hasAuthority("POLICE")

                        // 수사 가설 확인 항목 체크·해제
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/cases/*/hypotheses/*/checklist/*"
                        )
                        .hasAuthority("POLICE")

                        // ==================== 수사 종결 ====================

                        // 수사 종결 요청 — 상태 연결 완성과는 별개
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/cases/*/investigation/close"
                        )
                        .hasAuthority("POLICE")

                        // ==================== 사건 ====================

                        // 사건 접수
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/cases"
                        )
                        .hasAuthority("CITIZEN")

                        // AI 유사 사건 검색
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/cases/ai-search"
                        )
                        .hasAuthority("CITIZEN")

                        // 담당 경찰 배정
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/cases/*/officer"
                        )
                        .hasAuthority("ADMIN")

                        // 사건 접수 취소
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/cases/*/cancel"
                        )
                        .hasAuthority("ADMIN")

                        // 사건 종결
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/cases/*/close"
                        )
                        .hasAuthority("ADMIN")

                        // 내 신고·배정 사건 조회
                        // 일반 사건 상세 경로보다 먼저 지정
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/cases/my"
                        )
                        .hasAnyAuthority("CITIZEN", "POLICE")

                        // 사건 목록 조회
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/cases"
                        )
                        .hasAnyAuthority("CITIZEN", "POLICE", "ADMIN")

                        // 사건 상세 조회
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/cases/*"
                        )
                        .hasAnyAuthority("CITIZEN", "POLICE", "ADMIN")

                        // ==================== 분실물·습득물 ====================

                        // 물품 통계 조회
                        // 일반 물품 상세 경로보다 먼저 지정
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/items/statistics"
                        )
                        .hasAnyAuthority("POLICE", "ADMIN")

                        // 내 등록 물품 조회
                        // 일반 물품 상세 경로보다 먼저 지정
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/items/my"
                        )
                        .hasAuthority("CITIZEN")

                        // 물품 등록
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/items"
                        )
                        .hasAuthority("CITIZEN")

                        // 물품 목록 조회
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/items"
                        )
                        .hasAnyAuthority("CITIZEN", "POLICE", "ADMIN")

                        // 물품 이미지는 /items/{itemId}/image처럼 경로가 두 단계다.
                        // 아래 상세 조회의 /items/* 규칙에는 매칭되지 않으므로 별도로 허용한다.
                        // 목록·상세와 같은 범위의 로그인 사용자만 이미지 파일을 조회할 수 있다.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/items/*/image"
                        )
                        .hasAnyAuthority("CITIZEN", "POLICE", "ADMIN")

                        // 물품 상세 조회
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/items/*"
                        )
                        .hasAnyAuthority("CITIZEN", "POLICE", "ADMIN")

                        // 물품 수정
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/items/*"
                        )
                        .hasAnyAuthority("CITIZEN", "POLICE")

                        // 기존 분실물에 이미지 첨부
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/items/*/image"
                        )
                        .hasAuthority("CITIZEN")

                        // AI 물품 매칭
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/ai/item-matches"
                        )
                        .hasAuthority("CITIZEN")

                        // 채팅 설명만으로 점수를 확인하는 미리보기 역시 시민 전용이다.
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/ai/item-matches/preview"
                        )
                        .hasAuthority("CITIZEN")

                        // ==================== 반환 신청 ====================

                        // 반환 신청 등록
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/return-requests"
                        )
                        .hasAuthority("CITIZEN")

                        // 내 반환 신청 목록 조회
                        // 일반 반환 신청 상세 경로보다 먼저 지정
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/return-requests/my"
                        )
                        .hasAuthority("CITIZEN")

                        // 내 반환 신청 상세 조회
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/return-requests/my/*"
                        )
                        .hasAuthority("CITIZEN")

                        // 반환 신청 목록·상세는 경찰과 관리자가 조회할 수 있다.
                        // 상태 변경 PATCH는 아래에서 경찰만 허용한다.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/return-requests"
                        )
                        .hasAnyAuthority("POLICE", "ADMIN")

                        // 반환 신청 상세 조회
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/return-requests/*"
                        )
                        .hasAnyAuthority("POLICE", "ADMIN")

                        // 반환 신청 승인
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/return-requests/*/approve"
                        )
                        .hasAuthority("POLICE")

                        // 반환 신청 반려
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/return-requests/*/reject"
                        )
                        .hasAuthority("POLICE")

                        // 반환 완료 처리
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/return-requests/*/complete"
                        )
                        .hasAuthority("POLICE")

                        // Swagger UI 접근 허용
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        )
                        .permitAll()

                        // 아직 등록하지 않은 요청도 기존 설정대로 허용
                        // 모든 권한 확정 후 기본 정책을 별도로 결정
                        .anyRequest().denyAll()
                )

                // Authorization: Bearer 토큰이 오면 JWT 서명과 만료 여부를 검증
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(
                                        jwtAuthenticationConverter()
                                )
                        )
                );

        return http.build();

    }

    private void writeErrorResponse(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            HttpStatus status,
            String message
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        objectMapper.writeValue(
                response.getOutputStream(),
                Map.of(
                        "status", status.value(),
                        "message", message
                )
        );
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();

        authoritiesConverter.setAuthoritiesClaimName("role");

        authoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();

        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);

        return authenticationConverter;
    }

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 실제 프론트 주소로 변경
        configuration.setAllowedOrigins(List.of("http://localhost:5173"));
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
        );
        configuration.setAllowedHeaders(
                List.of("Authorization", "Content-Type")
        );

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
