package com.polink.item.dto;

/*
 * 분실물과 유사하다고 판단된 습득물 후보 한 건의 응답 DTO다.
 */
public record ItemMatchResponse(

        // 매칭 후보가 된 습득물의 물품 번호다. 예: F-0211
        String foundItemId,

        // 카테고리·물품명·장소 중 어떤 정보가 유사했는지 설명한다.
        // 실제 소유권을 확정하는 정보는 아니며, 반환 신청 전 참고용 후보 근거다.
        String reason
) {
}