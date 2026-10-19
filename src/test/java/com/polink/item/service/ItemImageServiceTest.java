package com.polink.item.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.mockito.Mockito.*;

/** 물품 이미지의 저장·확장자·크기 검증 규칙을 확인한다. */
class ItemImageServiceTest {

    // 테스트가 끝나면 자동으로 삭제되는 임시 폴더
    @TempDir
    Path tempDirectory;

    private ItemImageService itemImageService;
    private Path uploadDirectory;

    @BeforeEach
    void setUp() {
        // 실제 사용자 폴더 대신 테스트 전용 저장 폴더를 사용한다.
        uploadDirectory = tempDirectory.resolve("item").toAbsolutePath().normalize();
        itemImageService = new ItemImageService(uploadDirectory);
    }

    // 1. 이미지를 선택하지 않으면 저장 없이 null을 반환하는지 검사
    @Test
    @DisplayName("이미지를 첨부하지 않으면 저장하지 않는다")
    void skipMissingImage() {
        String imagePath = itemImageService.save(null);

        assertNull(imagePath);
        assertFalse(Files.exists(uploadDirectory));
    }

    // 2. 허용된 이미지가 실제 저장되고, 파일 내용이 같은지 검사
    @Test
    @DisplayName("허용된 이미지를 저장하고 경로를 반환한다")
    void saveValidImage() throws IOException {
        byte[] content = {1, 2, 3};
        MockMultipartFile image = new MockMultipartFile(
                "image", "wallet.PNG", "image/png", content
        );

        String imagePath = itemImageService.save(image);
        Path savedPath = Path.of(imagePath);

        assertEquals(uploadDirectory, savedPath.getParent());
        assertTrue(Files.exists(savedPath));
        assertTrue(savedPath.getFileName().toString().endsWith(".png"));
        assertArrayEquals(content, Files.readAllBytes(savedPath));
    }

    // 3. 내용이 없는 이미지는 저장하지 않는지 검사
    @Test
    @DisplayName("빈 이미지는 등록할 수 없다")
    void rejectEmptyImage() {
        MockMultipartFile image = new MockMultipartFile(
                "image", "empty.jpg", "image/jpeg", new byte[0]
        );

        assertThrows(IllegalArgumentException.class, () -> itemImageService.save(image));
        assertFalse(Files.exists(uploadDirectory));
    }

    // 4. PNG/JPG/JPEG/WEBP 외 확장자는 저장하지 않는지 검사
    @Test
    @DisplayName("지원하지 않는 이미지 확장자는 등록할 수 없다")
    void rejectUnsupportedExtension() {
        MockMultipartFile image = new MockMultipartFile(
                "image", "wallet.pdf", "application/pdf", new byte[]{1}
        );

        assertThrows(IllegalArgumentException.class, () -> itemImageService.save(image));
        assertFalse(Files.exists(uploadDirectory));
    }

    // 5. 2MB를 초과한 이미지는 실제 대용량 파일 없이 크기 정보만 가짜로 만들어 검사
    @Test
    @DisplayName("2MB를 초과한 이미지는 등록할 수 없다")
    void rejectOversizedImage() {
        MultipartFile image = mock(MultipartFile.class);
        when(image.isEmpty()).thenReturn(false);
        when(image.getSize()).thenReturn(2L * 1024 * 1024 + 1);

        assertThrows(IllegalArgumentException.class, () -> itemImageService.save(image));
        assertFalse(Files.exists(uploadDirectory));
    }

    // 6. 파일 읽기 실패 후, 중간에 생성된 빈 이미지가 정리되는지 검사
    @Test
    @DisplayName("이미지 저장 중 오류가 발생하면 생성된 파일을 삭제한다")
    void cleanUpFailedImageUpload() throws IOException {
        MultipartFile image = mock(MultipartFile.class);
        when(image.isEmpty()).thenReturn(false);
        when(image.getSize()).thenReturn(3L);
        when(image.getOriginalFilename()).thenReturn("item.jpg");
        when(image.getInputStream()).thenThrow(new IOException("테스트용 파일 읽기 실패"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> itemImageService.save(image)
        );

        assertInstanceOf(IOException.class, exception.getCause());
        try (var files = Files.list(uploadDirectory)) {
            assertEquals(0L, files.count());
        }
    }

    // 7. 저장 폴더 안의 이미지는 정상적으로 삭제되는지 검사
    @Test
    @DisplayName("저장된 이미지를 삭제한다")
    void deleteSavedImage() throws IOException {
        Files.createDirectories(uploadDirectory);
        Path savedPath = Files.write(uploadDirectory.resolve("item.jpg"), new byte[]{1});

        itemImageService.delete(savedPath.toString());

        assertFalse(Files.exists(savedPath));
    }

    // 8. 저장 폴더 밖의 파일은 삭제하지 못하게 막는지 검사
    @Test
    @DisplayName("저장 폴더 밖의 이미지는 삭제할 수 없다")
    void rejectOutsideImageDeletion() throws IOException {
        Path outsidePath = Files.write(tempDirectory.resolve("outside.jpg"), new byte[]{1});

        assertThrows(
                IllegalArgumentException.class,
                () -> itemImageService.delete(outsidePath.toString())
        );
        assertTrue(Files.exists(outsidePath));
    }
}
