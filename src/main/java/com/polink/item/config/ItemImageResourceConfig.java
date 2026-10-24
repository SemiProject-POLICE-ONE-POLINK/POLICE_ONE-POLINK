package com.polink.item.config;

import com.polink.item.service.ItemImageService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 물품 이미지의 공개 조회 URL과 실제 저장 폴더를 연결한다.
 *
 * <p>예를 들어 DB의 imagePath가 {@code /api/v1/item-images/item-123.png}이면
 * Spring MVC가 사용자 폴더의 {@code polink-uploads/item/item-123.png} 파일을 응답한다.</p>
 */
@Configuration
public class ItemImageResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // "/api/v1/item-images/**" 요청만 이 로컬 폴더의 정적 파일 조회로 처리한다.
        // 다른 API URL에는 영향을 주지 않아 팀원의 컨트롤러 라우팅과 충돌하지 않는다.
        // URL 접두사와 실제 저장 폴더는 ItemImageService의 공통 상수를 사용한다.
        // 이 설정을 통해 Item DTO의 imagePath를 프론트엔드 img src에 바로 사용할 수 있다.
        registry.addResourceHandler(ItemImageService.IMAGE_URL_PREFIX + "**")
                .addResourceLocations(
                        ItemImageService.defaultUploadDirectory().toUri().toString()
                );
    }
}
