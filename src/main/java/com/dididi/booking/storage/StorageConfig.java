package com.dididi.booking.storage;

import io.minio.MinioClient;
import jakarta.servlet.MultipartConfigElement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * Cau hinh luu tru file qua MinIO (S3-compatible).
 * Cau hinh qua app.storage.* (co default cho MinIO local) -> KHONG can sua application.yml.
 * Gioi han kich thuoc upload dat bang bean (khong dung property YAML).
 */
@Configuration
public class StorageConfig {

    @Bean
    public MinioClient minioClient(
            @Value("${app.storage.endpoint:http://localhost:9000}") String endpoint,
            @Value("${app.storage.access-key:minioadmin}") String accessKey,
            @Value("${app.storage.secret-key:minioadmin}") String secretKey) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        // Cong dong cho dang anh + video ngan -> nang gioi han (anh van bi chan 8MB, video 64MB tai service).
        factory.setMaxFileSize(DataSize.ofMegabytes(80));
        factory.setMaxRequestSize(DataSize.ofMegabytes(300));
        return factory.createMultipartConfig();
    }
}
