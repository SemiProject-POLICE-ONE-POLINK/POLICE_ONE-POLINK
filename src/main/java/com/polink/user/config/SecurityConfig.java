package com.polink.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
                // 현재는 세션 로그인을 사용하지 않는다.
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 현재 API는 세션·쿠키 인증을 사용하지 않으므로 비활성화한다.
                .csrf(AbstractHttpConfigurer::disable)

                // Spring Security의 기본 로그인 창과 Basic 인증은 사용하지 않는다.
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                // JWT 검증을 연결하기 전까지 모든 경로 허용(임시)
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().permitAll())
                // Authorization: Bearer 토큰이 오면 JWT 서명과 만료 여부를 검증
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults()));

        return http.build();
    }
}
