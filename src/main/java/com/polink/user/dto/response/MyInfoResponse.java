package com.polink.user.dto.response;

import com.polink.user.entity.User;
import com.polink.user.entity.UserRole;

// 로그인한 회원의 정보
public record MyInfoResponse(
        Integer userId,      // 회원 번호
        String userName,     // 로그인 아이디
        String name,         // 이름
        String phone,        // 연락처
        UserRole role        // 시민·경찰·관리자 권한
) {

    public static MyInfoResponse from(User user) {
        return new MyInfoResponse(
                user.getUserId(),
                user.getUserName(),
                user.getName(),
                user.getPhone(),
                user.getRole()
        );
    }
}
