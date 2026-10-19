package com.polink.item.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/** 물품 이미지 업로드에만 필요한 최대 파일 크기(2MB) 설정이다. */
@Configuration
public class ItemMultipartConfig {

    @Bean
    public MultipartConfigElement itemMultipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();

        // 이미지 한 장만 받으므로 요청 전체 크기도 동일하게 2MB로 제한한다.
        factory.setMaxFileSize(DataSize.ofMegabytes(2));
        factory.setMaxRequestSize(DataSize.ofMegabytes(2));

        return factory.createMultipartConfig();
    }
}
