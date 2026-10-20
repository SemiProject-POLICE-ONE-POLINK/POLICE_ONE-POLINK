package com.polink.user.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

// SecretKey 객체 준비
@Configuration
public class JwtConfig {

    // 환경 변수에 저장한 Base64 비밀키를 JWT 서명용 키로 변환
    @Bean
    public SecretKey jwtSecretKey(
            @Value("${app.jwt.secret-base64:}") String encodedSecret
    ) {

        if (encodedSecret.isBlank()) {
            throw new IllegalStateException(
                    "JWT 비밀키(app.jwt.secret-base64)가 설정되지 않았습니다. "
                            + "GenerateJwtKey로 생성해 application.yaml에 추가해주세요."
            );
        }

        // Base64 형식이 아니면 서버 시작을 중단
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encodedSecret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "JWT 비밀키는 Base64 형식이어야 합니다. GenerateJwtKey로 생성해주세요.",
                    exception
            );
        }

        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                    "JWT 비밀키는 32바이트 이상이어야 합니다."
            );
        }

        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    // 로그인 성공 시 액세스 토큰을 생성하는 도구
    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return NimbusJwtEncoder
                .withSecretKey(jwtSecretKey)
                .build();
    }

    // 요청으로 받은 액세스 토큰을 검증하는 도구
    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder
                .withSecretKey(jwtSecretKey)
                .build();
    }
}
