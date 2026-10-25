package com.polink.user.dto.response;

import java.util.List;

public record AdminUserListResponse(
        List<AdminUserResponse> users
) {
}