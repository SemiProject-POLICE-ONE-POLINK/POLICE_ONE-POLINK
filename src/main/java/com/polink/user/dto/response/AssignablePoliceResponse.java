package com.polink.user.dto.response;

public record AssignablePoliceResponse(
        Integer userId,
        String name,
        long investigatingCaseCount
) {
}
