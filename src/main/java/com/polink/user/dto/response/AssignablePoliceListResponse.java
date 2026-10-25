package com.polink.user.dto.response;

import java.util.List;

public record AssignablePoliceListResponse(
        List<AssignablePoliceResponse> officers
) {
}