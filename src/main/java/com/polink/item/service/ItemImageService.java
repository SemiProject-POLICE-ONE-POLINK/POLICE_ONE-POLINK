package com.polink.item.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 물품 등록 화면에서 선택한 이미지 한 장을 검증하고 로컬 디스크에 저장한다. */
@Service
public class ItemImageService {

    /** 요구사항의 최대 이미지 크기: 2MB */
    private static final long MAX_IMAGE_SIZE = 2 * 1024 * 1024;

    /** 요구사항에서 허용한 이미지 확장자 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp");

    /** 실행 PC의 사용자 폴더 아래에 물품 이미지를 저장한다. */
    private final Path uploadDirectory;

    public ItemImageService() {
        this(Path.of(System.getProperty("user.home"), "polink-uploads", "item"));
    }

    /** 테스트에서 임시 폴더를 사용하기 위한 생성자다. */
    ItemImageService(Path uploadDirectory) {
        this.uploadDirectory = uploadDirectory.toAbsolutePath().normalize();
    }

    /**
     * 이미지가 없으면 null을 반환한다. 이미지는 선택 입력값이기 때문이다.
     * 이미지가 있으면 검증 후 저장하고 DB에 기록할 절대 경로를 반환한다.
     */
    public String save(MultipartFile image) {
        if (image == null) {
            return null;
        }
        if (image.isEmpty()) {
            throw new IllegalArgumentException("빈 이미지는 첨부할 수 없습니다.");
        }
        if (image.getSize() > MAX_IMAGE_SIZE) {
            throw new IllegalArgumentException("이미지는 2MB 이하만 첨부할 수 있습니다.");
        }

        String originalFilename = image.getOriginalFilename();
        String extension = extractExtension(originalFilename);

        Path savedPath = null;

        try {
            Files.createDirectories(uploadDirectory);
            savedPath = Files.createTempFile(uploadDirectory, "item-", "." + extension);

            try (InputStream inputStream = image.getInputStream()) {
                Files.copy(inputStream, savedPath, StandardCopyOption.REPLACE_EXISTING);
            }

            return savedPath.toString();
        } catch (IOException exception) {
            // 저장 중 실패한 경우, 중간에 만든 빈 파일이 남지 않도록 정리한다.
            if (savedPath != null) {
                try {
                    Files.deleteIfExists(savedPath);
                } catch (IOException cleanupException) {
                    exception.addSuppressed(cleanupException);
                }
            }
            throw new IllegalStateException("이미지 저장에 실패했습니다.", exception);
        }
    }

    /** DB 저장이 실패해 롤백될 때, 이미 저장한 파일을 정리한다. */
    public void delete(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            return;
        }

        Path savedPath = Path.of(imagePath).toAbsolutePath().normalize();
        if (!uploadDirectory.equals(savedPath.getParent())) {
            throw new IllegalArgumentException("이미지 경로가 올바르지 않습니다.");
        }

        try {
            Files.deleteIfExists(savedPath);
        } catch (IOException exception) {
            throw new IllegalStateException("이미지 삭제에 실패했습니다.", exception);
        }
    }

    /**
     * DB에 저장된 이미지 경로를 브라우저 응답에 사용할 수 있는 실제 파일로 확인한다.
     * DB 값만 믿고 임의 경로를 읽지 않도록, 반드시 물품 이미지 전용 폴더의 일반 파일만 허용한다.
     */
    public Path resolveReadablePath(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            throw new IllegalArgumentException("첨부된 이미지가 없습니다.");
        }

        Path savedPath = Path.of(imagePath).toAbsolutePath().normalize();
        if (!uploadDirectory.equals(savedPath.getParent()) || !Files.isRegularFile(savedPath)) {
            throw new IllegalArgumentException("첨부된 이미지 파일을 찾을 수 없습니다.");
        }

        return savedPath;
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("이미지 파일명을 확인해주세요.");
        }

        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == originalFilename.length() - 1) {
            throw new IllegalArgumentException("이미지 확장자를 확인해주세요.");
        }

        String extension = originalFilename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("PNG, JPG, JPEG, WEBP 이미지만 첨부할 수 있습니다.");
        }

        return extension;
    }
}
