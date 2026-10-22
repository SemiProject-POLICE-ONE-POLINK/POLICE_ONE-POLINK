package com.polink.user.service;

import com.polink.user.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

// JWT 액세스 토큰 발급 담당 Service
@Service
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;             // JWT를 생성하고 서명하는 객체
    private final long accessTokenMinutes;          // 액세스 토큰의 유효 시간 (분 단위)

    public JwtTokenService(
            JwtEncoder jwtEncoder,
            @Value("${app.jwt.access-token-minutes}") long accessTokenMinutes
    ) {
        this.jwtEncoder = jwtEncoder;
        this.accessTokenMinutes = accessTokenMinutes;
    }

    // 로그인에 성공한 회원의 정보를 바탕으로 JWT 액세스 토큰 생성
    public String createAccessToken(User user) {

        // 현재 시각을 가져옴 (토큰 발급 및 만료 시각 계산에 사용)
        Instant now = Instant.now();

        // JWT에 포함할 사용자 정보(Claims) 설정
        JwtClaimsSet claims = JwtClaimsSet.builder()

                // 토큰의 주체(sub): 회원번호를 문자열로 변환하여 저장
                .subject(user.getUserId().toString())

                // 토큰 발급 시각(iat): 현재 시각 저장
                .issuedAt(now)

                // 토큰 만료 시각(exp): 현재 시각 + 설정된 유효 시간
                .expiresAt(now.plus(
                        accessTokenMinutes,
                        ChronoUnit.MINUTES
                ))

                // 사용자 역할(role), 아이디(userName), 이름(name), 연락처(phone)
                .claim("role", user.getRole().name())
                .claim("userName", user.getUserName())
                .claim("name", user.getName())
                .claim("phone", user.getPhone())

                // 설정한 정보를 바탕으로 Claims 객체 생성
                .build();

        // JWT 서명 알고리즘을 HS256으로 설정
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        // Header와 Claims를 이용해 JWT를 생성하고 문자열로 반환
        return jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }

}
