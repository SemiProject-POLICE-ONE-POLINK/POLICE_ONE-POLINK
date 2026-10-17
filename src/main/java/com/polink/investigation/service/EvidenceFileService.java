package com.polink.investigation.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

@Service
public class EvidenceFileService {

    // 모든 요청에 공통으로 적용하는 최대 파일 크기: 10MB
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    // 등록 가능한 파일 확장자 목록
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "pdf", "mp4");

    // 저장 위치: 사용자 홈/polink-uploads/evidence
    // 절대경로로 변환하고 불필요한 "."과 ".." 정리
    private final Path uploadDirectory = Path.of(
            System.getProperty("user.home"),
            "polink-uploads",
            "evidence"
    ).toAbsolutePath().normalize();

    // 첨부파일을 검증·저장하고, DB에 기록할 경로 반환
    public String save(MultipartFile file) {

        // 1. 파일이 전달되지 않았다면 저장 없이 null 반환
        if (file == null) {
            return null;
        }
        // 2. 첨부했지만 내용이 없는 파일은 등록 불가
        if (file.isEmpty()) {
            throw new IllegalArgumentException(
                    "빈 파일은 첨부할 수 없습니다."
            );
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "파일은 10MB 이하만 첨부할 수 있습니다."
            );
        }

        // 4. 업로드 요청에 포함된 원본 파일명 가져오기
        String originalFilename = file.getOriginalFilename();

        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException(
                    "파일명을 확인해주세요."
            );
        }

        // 5. 확장자를 구분할 마지막 "." 위치 확인
        // ex) "scene.photo.jpg"에서는 "jpg" 앞의 점을 찾음
        int dotIndex = originalFilename.lastIndexOf(".");

        /* 파일명 또는 확장자 구분점이 없으면 등록 불가
         * ex) 사진.jpg → 점 앞에 이름이 있고 점도 있다면 통과
         * "사진"처럼 점이 없거나, ".jpg"처럼 이름이 없으면 등록 중단
         * */
        if (dotIndex <= 0) {
            throw new IllegalArgumentException(
                    "파일 확장자를 확인해주세요."
            );
        }

        // 마지막 점 다음부터 가져와 확장자 추출
        // JPG처럼 대문자로 전달되어도 jpg로 통일
        String extension = originalFilename
                .substring(dotIndex + 1)
                .toLowerCase(Locale.ROOT);

        // 6. 허용 목록에 없는 확장자는 등록 중단
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "지원하지 않는 파일 형식입니다."
            );
        }

        // 저장할 파일의 경로를 담는 변수
        Path savedPath = null;

        try {
            // 7. polink-uploads 폴더가 없으면 상위 폴더까지 생성
            Files.createDirectories(uploadDirectory);

            // 8. 중복되지 않는 이름의 빈 파일 생성
            // 예: evidence-123456789.jpg
            savedPath = Files.createTempFile(
                    uploadDirectory,
                    "evidence-",
                    "." + extension
            );

            // 9. 업로드된 파일을 읽는 스트림 열기
            // try 블록이 끝나면 스트림은 자동으로 닫힘
            try (InputStream inputStream = file.getInputStream()) {

                // 생성해 둔 빈 파일에 업로드 파일 내용 복사
                // REPLACE_EXISTING: 대상 파일이 이미 있으므로 덮어쓰기
                Files.copy(
                        inputStream,    // 읽어올 업로드 파일 내용
                        savedPath,      // 내용을 저장할 파일 위치
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
            }

            // 10. 저장된 경로를 문자열로 반환
            // EvidenceService에서 filePath에 설정할 값
            return savedPath.toString();

        } catch (IOException exception) {

                // 파일 생성·읽기·복사 중 오류가 발생한 경우 처리
                // 생성된 파일이 있다면 불완전한 파일이 남지 않도록 삭제 시도
                if (savedPath != null) {
                    try {
                        Files.deleteIfExists(savedPath);
                    } catch (IOException cleanupException) {

                        // 삭제까지 실패 시 실패 원인도 함께 기록
                        exception.addSuppressed(cleanupException);
                    }
                }

            // 저장 실패 메시지와 실제 오류 원인을 함께 전달
                throw new IllegalStateException("첨부파일 저장에 실패했습니다.", exception);
        }
    }

    // DB 등록 실패 시 저장된 첨부 파일 삭제
    public void delete(String filePath) {

        // 첨부파일이 없으면 종료
        if (filePath == null || filePath.isBlank()) {
            return;
        }

        Path savedPath = Path.of(filePath)
                .toAbsolutePath()
                .normalize();

        // 증거물 저장 폴더 안의 파일만 삭제 허용
        if (!uploadDirectory.equals(savedPath.getParent())) {
            throw new IllegalArgumentException(
                    "첨부파일 경로가 올바르지 않습니다."
            );
        }

        try {
            // 파일이 존재하면 삭제
            Files.deleteIfExists(savedPath);

        } catch (IOException exception) {
            throw new IllegalStateException("첨부파일 삭제에 실패했습니다.", exception);
        }
    }
}