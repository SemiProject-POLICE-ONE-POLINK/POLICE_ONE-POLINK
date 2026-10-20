package com.polink.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

// 로그인 요청 DTO
// 생성자와 입력값을 읽을 Getter를 Lombok이 자동 생성
@Getter
@NoArgsConstructor
public class LoginRequest {

        @NotBlank(message = "아이디를 입력해주세요.")
        private String userName;

        @NotBlank(message = "비밀번호를 입력해주세요.")
        private String password;
}
