package com.polink.investigation.dto.response;

// 실제 파일 응답을 만들 때 사용할 내부 데이터
public record EvidenceFileResponse(
        byte[] content,       // 실제 파일 내용
        String contentType,   // 파일 형식
        String filename       // 응답에 사용할 파일명
) {

}
