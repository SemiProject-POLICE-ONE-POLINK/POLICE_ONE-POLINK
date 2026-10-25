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

    /**
     * 클라이언트가 DB에 저장된 imagePath를 그대로 사용해 이미지를 조회하는 공개 URL 접두사다.
     * 실제 디스크 절대 경로를 API 응답으로 노출하지 않기 위해 사용한다.
     */
    public static final String IMAGE_URL_PREFIX = "/api/v1/item-images/";

    /** 요구사항의 최대 이미지 크기: 2MB */
    private static final long MAX_IMAGE_SIZE = 2 * 1024 * 1024;

    /** 요구사항에서 허용한 이미지 확장자 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp");

    /** 실행 PC의 사용자 폴더 아래에 물품 이미지를 저장한다. */
    private final Path uploadDirectory;

    public ItemImageService() {
        this(defaultUploadDirectory());
    }

    /**
     * 이미지 저장 서비스와 정적 리소스 설정이 반드시 같은 폴더를 보도록 공통 경로를 제공한다.
     * 두 위치를 각각 작성하면 저장은 됐지만 URL에서는 찾지 못하는 오류가 생길 수 있다.
     */
    public static Path defaultUploadDirectory() {
        return Path.of(System.getProperty("user.home"), "polink-uploads", "item")
                .toAbsolutePath()
                .normalize();
    }

    /** 테스트에서 임시 폴더를 사용하기 위한 생성자다. */
    ItemImageService(Path uploadDirectory) {
        this.uploadDirectory = uploadDirectory.toAbsolutePath().normalize();
    }

    /**
     * 이미지가 없으면 null을 반환한다. 이미지는 선택 입력값이기 때문이다.
     * 이미지가 있으면 검증 후 저장하고 DB에 기록할 공개 URL을 반환한다.
     * 실제 서버 파일 경로는 외부 API에 노출하지 않는다.
     */
    public String save(MultipartFile image) {
        // 이미지 파라미터는 선택값이다. 이미지 없이 물품을 등록하는 기존 흐름도 유지한다.
        if (image == null) {
            return null;
        }

        // 파일 내용이 없는 요청은 저장 폴더를 만들거나 DB 값을 남기지 않고 즉시 거절한다.
        if (image.isEmpty()) {
            throw new IllegalArgumentException("빈 이미지는 첨부할 수 없습니다.");
        }

        // 애플리케이션 서버 디스크를 보호하기 위해 요구사항의 2MB 제한을 먼저 검사한다.
        if (image.getSize() > MAX_IMAGE_SIZE) {
            throw new IllegalArgumentException("이미지는 2MB 이하만 첨부할 수 있습니다.");
        }

        // 원본 파일명에서 확장자를 꺼내 허용된 이미지 형식인지 확인한다.
        String originalFilename = image.getOriginalFilename();
        String extension = extractExtension(originalFilename);

        Path savedPath = null;

        try {
            // 최초 업로드일 수 있으므로 폴더가 없으면 먼저 생성한다.
            Files.createDirectories(uploadDirectory);

            // 사용자 파일명 대신 임시 난수 파일명을 사용해 이름 충돌과 경로 조작을 피한다.
            savedPath = Files.createTempFile(uploadDirectory, "item-", "." + extension);

            // 업로드 스트림은 try-with-resources로 닫아 파일 핸들이 남지 않게 한다.
            try (InputStream inputStream = image.getInputStream()) {
                Files.copy(inputStream, savedPath, StandardCopyOption.REPLACE_EXISTING);
            }

            // DB에는 C:\\... 같은 서버 내부 경로가 아니라 브라우저가 요청할 URL만 저장한다.
            return toPublicUrl(savedPath);
          
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
        // 이미지 없이 등록된 물품은 삭제할 실제 파일이 없으므로 아무 작업도 하지 않는다.
        if (imagePath == null || imagePath.isBlank()) {
            return;
        }

        // 신규 URL 형식과 예전에 저장된 절대 경로 형식을 모두 해석한다.
        // 기존 데이터가 있어도 트랜잭션 롤백 시 파일 정리가 깨지지 않도록 하기 위함이다.
        Path savedPath = resolveStoredPath(imagePath);

        // URL을 조작해 다른 폴더의 파일을 삭제하지 못하도록 업로드 폴더의 직속 파일만 허용한다.
        if (!uploadDirectory.equals(savedPath.getParent())) {
            throw new IllegalArgumentException("이미지 경로가 올바르지 않습니다.");
        }

        try {
            // 이미 다른 작업에서 지운 파일이라도 롤백 흐름은 정상 종료되도록 deleteIfExists를 사용한다.
            Files.deleteIfExists(savedPath);
        } catch (IOException exception) {
            throw new IllegalStateException("이미지 삭제에 실패했습니다.", exception);
        }
    }

feat/item-overall-enhancements
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
=======
    /** 저장된 파일의 이름만 이용해 외부에서 접근 가능한 URL을 만든다. */
    private String toPublicUrl(Path savedPath) {
        return IMAGE_URL_PREFIX + savedPath.getFileName();
    }

    /**
     * DB imagePath를 실제 저장 파일 경로로 바꾼다.
     *
     * <p>URL에 상위 경로 이동 문자(../)를 넣는 공격을 막기 위해, URL 방식은 파일명 하나만 허용한다.
     * 절대 경로 방식은 URL 도입 전 데이터의 롤백 정리 호환을 위해서만 지원하고,
     * 이후 부모 폴더 검증을 다시 수행한다.</p>
     */
    Path resolveStoredPath(String imagePath) {
        if (imagePath.startsWith(IMAGE_URL_PREFIX)) {
            String fileName = imagePath.substring(IMAGE_URL_PREFIX.length());
            if (fileName.isBlank()
                    || !Path.of(fileName).getFileName().toString().equals(fileName)) {
                throw new IllegalArgumentException("이미지 경로가 올바르지 않습니다.");
            }

            return uploadDirectory.resolve(fileName).toAbsolutePath().normalize();
        }

        return Path.of(imagePath).toAbsolutePath().normalize();
 main
    }

    private String extractExtension(String originalFilename) {
        // 확장자 검증은 Content-Type만 믿지 않고 파일명 기준으로 한 번 더 수행한다.
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("이미지 파일명을 확인해주세요.");
        }

        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == originalFilename.length() - 1) {
            throw new IllegalArgumentException("이미지 확장자를 확인해주세요.");
        }

        String extension = originalFilename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        // 대문자 확장자(PNG 등)도 소문자로 통일한 뒤 허용 목록과 비교한다.
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("PNG, JPG, JPEG, WEBP 이미지만 첨부할 수 있습니다.");
        }

        return extension;
    }
}
