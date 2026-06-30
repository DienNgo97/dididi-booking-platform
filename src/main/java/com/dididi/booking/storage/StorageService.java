package com.dididi.booking.storage;

import com.dididi.booking.common.exception.BusinessException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

/**
 * Luu/doc file tren MinIO. Bucket duoc tao lazy (lan upload dau tien) de app van khoi dong
 * duoc khi MinIO chua chay; chi thao tac upload/serve moi can MinIO song.
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final MinioClient minio;
    private final String bucket;
    private volatile boolean bucketReady = false;

    public StorageService(MinioClient minio, @Value("${app.storage.bucket:dididi-hotels}") String bucket) {
        this.minio = minio;
        this.bucket = bucket;
    }

    private void ensureBucket() {
        if (bucketReady) {
            return;
        }
        try {
            boolean exists = minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Tao bucket MinIO: {}", bucket);
            }
            bucketReady = true;
        } catch (Exception e) {
            throw new BusinessException("STORAGE_UNAVAILABLE",
                    "Không kết nối được MinIO (" + e.getMessage() + ")", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    /** Upload 1 file anh, tra ve object key. */
    public String upload(MultipartFile file, String folder) {
        return upload(file, folder, java.util.Set.of("image/"));
    }

    /**
     * Upload 1 file voi danh sach tien to content-type cho phep (vd "image/", "video/").
     * Dung cho mang xa hoi (anh + video). Tra ve object key.
     */
    public String upload(MultipartFile file, String folder, java.util.Set<String> allowedPrefixes) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("EMPTY_FILE", "File rỗng", HttpStatus.BAD_REQUEST);
        }
        String contentType = file.getContentType();
        boolean ok = contentType != null
                && allowedPrefixes.stream().anyMatch(contentType::startsWith);
        if (!ok) {
            throw new BusinessException("MEDIA_NOT_ALLOWED",
                    "Định dạng tệp không được hỗ trợ", HttpStatus.BAD_REQUEST);
        }
        // Chan cac dinh dang co the chua script (SVG/HTML/XML) -> tranh stored XSS khi serve lai.
        String ctLower = contentType.toLowerCase();
        if (ctLower.contains("svg") || ctLower.contains("xml") || ctLower.contains("html") || ctLower.contains("script")) {
            throw new BusinessException("MEDIA_NOT_ALLOWED",
                    "Định dạng tệp không được hỗ trợ (SVG/HTML bị chặn vì lý do bảo mật)", HttpStatus.BAD_REQUEST);
        }
        ensureBucket();
        String key = folder + "/" + UUID.randomUUID() + extOf(file.getOriginalFilename());
        try (InputStream in = file.getInputStream()) {
            minio.putObject(PutObjectArgs.builder()
                    .bucket(bucket).object(key)
                    .stream(in, file.getSize(), -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new BusinessException("UPLOAD_FAILED",
                    "Tải tệp lên thất bại (" + e.getMessage() + ")", HttpStatus.BAD_GATEWAY);
        }
        return key;
    }

    /** Xoa 1 object (best-effort): loi xoa chi log, khong nem ra de van xoa duoc ban ghi DB. */
    public void remove(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            ensureBucket();
            minio.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            log.warn("Xoa object MinIO that bai (key={}): {}", key, e.getMessage());
        }
    }

    /** Doc object ve bytes + content-type de serve. */
    public StoredObject load(String key) {
        ensureBucket();
        try (GetObjectResponse resp = minio.getObject(
                GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            byte[] bytes = resp.readAllBytes();
            String ct = resp.headers().get("Content-Type");
            return new StoredObject(bytes, ct != null ? ct : "application/octet-stream");
        } catch (Exception e) {
            throw new BusinessException("FILE_NOT_FOUND", "Không đọc được ảnh", HttpStatus.NOT_FOUND);
        }
    }

    private static String extOf(String filename) {
        if (filename == null) {
            return "";
        }
        int i = filename.lastIndexOf('.');
        return i >= 0 ? filename.substring(i).toLowerCase() : "";
    }

    public record StoredObject(byte[] bytes, String contentType) {
        /**
         * Content-type AN TOAN de tra ve client. Khong tin content-type goc (do nguoi dung
         * kiem soat khi upload) -> chi cho phep danh sach trang anh/video; con lai tra octet-stream
         * (trinh duyet se tai ve, khong render/thuc thi) de chong stored XSS qua content-type.
         */
        public String safeContentType() {
            String ct = contentType == null ? "" : contentType.toLowerCase().trim();
            return switch (ct) {
                case "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp", "image/avif",
                        "video/mp4", "video/webm", "video/ogg", "video/quicktime" -> ct;
                default -> "application/octet-stream";
            };
        }
    }
}
