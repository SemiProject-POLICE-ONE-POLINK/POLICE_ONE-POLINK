package com.polink.item.returnrequest.dto;

import com.polink.item.entity.Item;
import com.polink.item.entity.ItemStatus;
import com.polink.item.returnrequest.entity.ReturnRequest;
import com.polink.item.returnrequest.entity.ReturnRequestStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

/*
 * 반환 신청 한 건의 상세 조회 응답 DTO다.
 * 경찰은 전체 반환 신청 상세를 조회할 수 있고,
 * 시민은 본인이 신청한 반환 신청 상세만 조회할 수 있게 만들 예정이다.
 * User 도메인을 직접 조회하지 않기 위해
 * 신청자와 처리 경찰관의 이름 대신 회원 번호만 반환한다.
 */
public record ReturnRequestDetailResponse(

        // 반환 신청 DB 번호다.
        Integer requestId,

        // 반환 신청을 만든 시민의 회원 번호다.
        Integer requesterId,

        // ===== 분실물 상세 정보 =====

        // 분실물 번호다. 예: L-0116
        String lostItemId,

        // 분실물 이름이다.
        String lostItemName,

        // 분실물 상세 설명이다.
        String lostItemDescription,

        // 분실물이 발생한 장소다.
        String lostItemLocation,

        // 분실물 발생일이다.
        LocalDate lostOccurredAt,

        // 분실물의 현재 상태다.
        ItemStatus lostItemStatus,

        // 분실물에 연결된 이미지 파일 경로다. 이미지가 없으면 null이다.
        String lostImagePath,

        // ===== 습득물 상세 정보 =====

        // 습득물 번호다. 예: F-0216
        String foundItemId,

        // 습득물 이름이다.
        String foundItemName,

        // 습득물 상세 설명이다.
        String foundItemDescription,

        // 습득물이 발견된 장소다.
        String foundItemLocation,

        // 습득물 발생일이다.
        LocalDate foundOccurredAt,

        // 습득물의 현재 상태다.
        ItemStatus foundItemStatus,

        // 습득물에 연결된 이미지 파일 경로다. 이미지가 없으면 null이다.
        String foundImagePath,

        // 시민이 반환을 신청한 사유다.
        String requestReason,

        // 반환 신청의 현재 처리 상태다.
        ReturnRequestStatus status,

        // 반려 상태일 때만 값이 있는 반려 사유다.
        // 승인·대기 상태에서는 빈 문자열이다.
        String rejectReason,

        // 승인 또는 반려를 처리한 경찰관의 회원 번호다.
        // 아직 처리되지 않은 PENDING 상태에서는 null이다.
        Integer processedBy,

        // 시민이 반환 신청을 만든 서버 시각이다.
        LocalDateTime createdAt,

        // 경찰이 승인 또는 반려를 처리한 서버 시각이다.
        // PENDING 상태에서는 null이다.
        LocalDateTime processedAt,

        // 실제 물품 인계를 완료한 경찰관의 회원 번호다.
        // 아직 반환 완료 전이면 null이다.
        Integer completedBy,

        // 실제 물품 인계가 완료된 서버 시각이다.
        // 아직 반환 완료 전이면 null이다.
        LocalDateTime completedAt
) {

    /*
     * 반환 신청 Entity와 연결된 분실물·습득물 Entity를
     * 상세 조회 응답 DTO로 변환한다.
     * ReturnRequest는 물품 번호만 가지고 있으므로,
     * Service에서 실제 Item Entity 두 건을 조회한 뒤 이 메서드로 전달한다.
     */
    public static ReturnRequestDetailResponse from(
            ReturnRequest returnRequest,
            Item lostItem,
            Item foundItem
    ) {
        return new ReturnRequestDetailResponse(
                // ===== 반환 신청 기본 정보 =====
                returnRequest.getRequestId(),
                returnRequest.getRequesterId(),

                // ===== 분실물 정보 =====
                lostItem.getItemId(),
                lostItem.getItemName(),
                lostItem.getDescription(),
                lostItem.getLocation(),
                lostItem.getOccurredAt().toLocalDate(),
                lostItem.getStatus(),
                lostItem.getImagePath(),

                // ===== 습득물 정보 =====
                foundItem.getItemId(),
                foundItem.getItemName(),
                foundItem.getDescription(),
                foundItem.getLocation(),
                foundItem.getOccurredAt().toLocalDate(),
                foundItem.getStatus(),
                foundItem.getImagePath(),

                // ===== 반환 신청 처리 정보 =====
                returnRequest.getRequestReason(),
                returnRequest.getStatus(),
                returnRequest.getRejectReason(),
                returnRequest.getProcessedBy(),
                returnRequest.getCreatedAt(),
                returnRequest.getProcessedAt(),

                // ===== 실제 반환 완료 정보 =====
                returnRequest.getCompletedBy(),
                returnRequest.getCompletedAt()
        );
    }
}