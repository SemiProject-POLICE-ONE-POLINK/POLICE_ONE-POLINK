package com.polink.item.returnrequest.dto;

import java.util.List;

/*
 * 로그인한 시민이 본인 반환 신청 목록을 조회할 때 반환하는 응답 DTO다.
 * 목록 데이터와 페이지 정보를 함께 제공해 프론트엔드가 페이지 이동 UI를 만들 수 있게 한다.
 */
public record MyReturnRequestListResponse(

        // 현재 페이지에 포함된 반환 신청 목록이다.
        List<ReturnRequestListResponse> items,

        // 현재 페이지 번호다. 첫 페이지는 0이다.
        int page,

        // 한 페이지에 요청한 조회 개수다.
        int size,

        // 조건에 맞는 본인 반환 신청의 전체 개수다.
        long totalElements,

        // 전체 페이지 개수다.
        int totalPages,

        // 현재 페이지가 첫 페이지인지 나타낸다.
        boolean first,

        // 현재 페이지가 마지막 페이지인지 나타낸다.
        boolean last
) {
}