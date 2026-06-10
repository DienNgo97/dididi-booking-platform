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
        if (file == null || file.isEmpty()) {
            throw new BusinessException("EMPTY_FILE", "File rỗng", HttpStatus.BAD_REQUEST);
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException("NOT_IMAGE", "Chỉ chấp nhận file ảnh (image/*)", HttpStatus.BAD_REQUEST);
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
                    "Tải ảnh lên thất bại (" + e.getMessage() + ")", HttpStatus.BAD_GATEWAY);
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
    }
}
