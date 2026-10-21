package com.polink.investigation.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EvidenceFileServiceTest {

    // 테스트용 임시 폴더 생성, 테스트 종료 후 자동 정리
    @TempDir
    Path tempDirectory;

    private EvidenceFileService evidenceFileService;
    private Path uploadDirectory;

    @BeforeEach
    void setUp() {
        evidenceFileService = new EvidenceFileService();

        // 실제 업로드 폴더 대신 테스트용 저장 경로 사용
        uploadDirectory = tempDirectory
                .resolve("evidence")
                .toAbsolutePath()
                .normalize();

        ReflectionTestUtils.setField(
                evidenceFileService,
                "uploadDirectory",
                uploadDirectory
        );
    }

    // 1. 파일을 첨부하지 않으면 저장 없이 null을 반환하는지 검사
    @Test
    @DisplayName("첨부파일이 없으면 저장하지 않는다")
    void skipMissingFile() {
        String filePath = evidenceFileService.save(null);

        assertNull(filePath);
        assertFalse(Files.exists(uploadDirectory));
    }

    // 2. 허용된 파일의 내용이 실제 파일에 동일하게 저장되는지 검사
    @Test
    @DisplayName("허용된 파일을 저장하고 경로를 반환한다")
    void saveValidFile() throws IOException {
        // 준비: 파일 복사 확인용 데이터
        byte[] content = {1, 2, 3};

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "scene.JPG",
                "image/jpeg",
                content
        );

        // 실행
        String filePath = evidenceFileService.save(file);
        Path savedPath = Path.of(filePath);

        // 확인: 지정한 폴더에 파일이 생성됐는지 검사
        assertEquals(uploadDirectory, savedPath.getParent());
        assertTrue(Files.exists(savedPath));

        // 확인: 대문자 확장자가 소문자로 통일됐는지 검사
        assertTrue(savedPath.getFileName().toString().endsWith(".jpg"));

        // 확인: 업로드한 내용과 저장된 내용이 같은지 검사
        assertArrayEquals(content, Files.readAllBytes(savedPath));
    }

    // 3. 내용이 없는 파일은 예외가 발생하고 저장되지 않는지 검사
    @Test
    @DisplayName("빈 파일은 등록할 수 없다")
    void rejectEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.jpg",
                "image/jpeg",
                new byte[0]
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> evidenceFileService.save(file)
        );

        assertFalse(Files.exists(uploadDirectory));
    }

    // 4. 허용하지 않은 확장자는 예외가 발생하고 저장되지 않는지 검사
    @Test
    @DisplayName("지원하지 않는 확장자는 등록할 수 없다")
    void rejectUnsupportedExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "program.exe",
                "application/octet-stream",
                new byte[]{1, 2, 3}
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> evidenceFileService.save(file)
        );

        assertFalse(Files.exists(uploadDirectory));
    }

    // 5. 10MB를 초과한 파일은 저장 전에 거부되는지 검사
    @Test
    @DisplayName("2MB를 초과한 파일은 등록할 수 없다")
    void rejectOversizedFile() {
        // 실제 대용량 파일 대신 크기 정보를 반환하는 가짜 파일 사용
        MultipartFile file = mock(MultipartFile.class);

        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(2L * 1024 * 1024 + 1);

        assertThrows(
                IllegalArgumentException.class,
                () -> evidenceFileService.save(file)
        );

        assertFalse(Files.exists(uploadDirectory));
    }

    // 6. 파일 읽기에 실패하면 이미 생성한 빈 파일도 정리되는지 검사
    @Test
    @DisplayName("저장 중 오류가 발생하면 생성된 파일을 삭제한다")
    void cleanUpFailedUpload() throws IOException {
        // 준비: 파일 정보를 확인한 뒤, 내용을 읽을 때 실패하는 상황
        MultipartFile file = mock(MultipartFile.class);

        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(3L);
        when(file.getOriginalFilename()).thenReturn("scene.jpg");
        when(file.getInputStream())
                .thenThrow(new IOException("테스트용 파일 읽기 실패"));

        // 실행 및 확인: 저장 실패 예외가 발생하는지 검사
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> evidenceFileService.save(file)
        );

        // 확인: 원래 파일 읽기 오류가 보존됐는지 검사
        assertInstanceOf(IOException.class, exception.getCause());

        // 확인: 생성된 파일이 정리되어 저장 폴더가 비어 있는지 검사
        try (var files = Files.list(uploadDirectory)) {
            assertEquals(0L, files.count());
        }
    }

    // 7. 저장 폴더 안에 있는 파일을 정상적으로 삭제하는지 검사
    @Test
    @DisplayName("저장된 첨부파일을 삭제한다")
    void deleteSavedFile() throws IOException {
        Files.createDirectories(uploadDirectory);

        Path savedPath = Files.write(
                uploadDirectory.resolve("scene.jpg"),
                new byte[]{1, 2, 3}
        );

        evidenceFileService.delete(savedPath.toString());

        assertFalse(Files.exists(savedPath));
    }

    // 8. 저장 폴더 밖의 파일은 삭제하지 못하도록 막는지 검사
    @Test
    @DisplayName("저장 폴더 밖의 파일은 삭제할 수 없다")
    void rejectOutsideFileDeletion() throws IOException {
        Path outsidePath = Files.write(
                tempDirectory.resolve("outside.jpg"),
                new byte[]{1, 2, 3}
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> evidenceFileService.delete(outsidePath.toString())
        );

        // 삭제가 거부되어 원래 파일이 남아 있는지 검사
        assertTrue(Files.exists(outsidePath));
    }
}