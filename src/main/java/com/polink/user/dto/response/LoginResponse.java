package com.polink.user.dto.response;

import com.polink.user.entity.UserRole;
import lombok.Getter;

// 로그인 정보가 일치할 시 반환할 응답 DTO
@Getter
public class LoginResponse {

    private final Integer userId;  // 회원 번호
    private final String userName; // 사용자 아이디
    private final String name;     // 화면에 표시할 이름
    private final UserRole role;   // 시민·경찰·관리자 구분
    private final String accessToken; // API 요청에 사용할 JWT

    public LoginResponse(Integer userId, String userName, String name, UserRole role, String accessToken) {
        this.userId = userId;
        this.userName = userName;
        this.name = name;
        this.role = role;
        this.accessToken = accessToken;
    }
}
