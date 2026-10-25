package com.polink.investigation.dto.response;

import com.polink.investigation.entity.Evidence;
import com.polink.investigation.entity.EvidenceType;

import java.time.LocalDateTime;

// 증거물 목록에 표시할 정보
public record EvidenceListResponse(
        String evidenceId,           // 증거번호
        String evidenceName,         // 증거물명
        EvidenceType evidenceType,   // 증거물 유형
        LocalDateTime acquiredAt,    // 확보 일시
        String acquiredLocation,     // 확보 장소
        String description,          // 메모
        boolean fileAttached,        // 첨부파일 유무
        String fileUrl               // 첨부파일 조회 API 주소
) {

    // 증거물 Entity를 목록 조회 응답 DTO로 변환
    public static EvidenceListResponse from(Evidence evidence) {
        String fileUrl = fileUrlOf(evidence);

        return new EvidenceListResponse(
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

    // 서버 저장 경로 대신 인증이 필요한 파일 조회 주소 생성
    public static String fileUrlOf(Evidence evidence) {
        String filePath = evidence.getFilePath();

        // 첨부파일이 없으면 조회 주소도 없음
        if (filePath == null || filePath.isBlank()) {
            return null;
        }

        return "/api/v1/cases/"
                + evidence.getCaseFile().getCaseId()
                + "/evidences/"
                + evidence.getEvidenceId()
                + "/file";
    }
}
