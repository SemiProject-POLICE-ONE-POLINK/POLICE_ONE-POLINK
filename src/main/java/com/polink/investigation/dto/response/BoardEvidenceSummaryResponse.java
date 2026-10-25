package com.polink.investigation.dto.response;

import com.polink.investigation.entity.Evidence;
import com.polink.investigation.entity.EvidenceType;

import java.time.LocalDateTime;

// 수사보드에 표시할 증거물 요약 DTO
public record BoardEvidenceSummaryResponse(

        String evidenceId,          // 상세 조회 등에 사용할 증거물 번호
        String evidenceName,        // 증거물명
        EvidenceType evidenceType,  // 증거물 유형
        LocalDateTime acquiredAt,   // 확보 일시
        String acquiredLocation,    // 확보 장소
        String description,         // 메모
        boolean fileAttached,       // 첨부파일 유무
        String fileUrl              // 첨부파일 조회 API 주소
) {

    // 증거물 Entity를 수사보드 요약 응답으로 변환
    public static BoardEvidenceSummaryResponse from(Evidence evidence) {

        // 목록 DTO와 같은 방식으로 파일 조회 주소 생성
        String fileUrl = EvidenceListResponse.fileUrlOf(evidence);

        return new BoardEvidenceSummaryResponse(
                evidence.getEvidenceId(),
                evidence.getEvidenceName(),
                evidence.getEvidenceType(),
                evidence.getAcquiredAt(),
                evidence.getAcquiredLocation(),
                evidence.getDescription(),
                fileUrl != null,
                fileUrl
        );
    }
}
