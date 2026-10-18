package com.polink.user.dto.response;

import lombok.Getter;

@Getter
public class SignupResponse {

    private final Integer userId;
    private final String userName;

    public SignupResponse(Integer userId, String userName) {
        this.userId = userId;
        this.userName = userName;
    }
}
