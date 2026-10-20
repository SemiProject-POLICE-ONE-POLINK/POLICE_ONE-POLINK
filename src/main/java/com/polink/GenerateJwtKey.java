package com.polink;

import java.security.SecureRandom;
import java.util.Base64;

/* JWT 비밀키 생성 도구 — 실행 결과를 각자 application.yaml의 app.jwt.secret-base64에 입력
* 양식은 application-example.yaml 파일 참고
* */
public class GenerateJwtKey {
    public static void main(String[] args) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        System.out.println(Base64.getEncoder().encodeToString(key));
    }
}
