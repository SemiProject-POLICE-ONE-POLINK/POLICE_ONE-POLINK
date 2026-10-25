package com.polink.user.dto.response;

import com.polink.user.entity.UserRole;

public record AdminUserResponse(
        Integer userId,
        String userName,
        String name,
        String phone,
        UserRole role
) {
}