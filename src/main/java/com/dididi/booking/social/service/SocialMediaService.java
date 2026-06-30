package com.dididi.booking.social.service;

import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.social.domain.entity.PostMedia;
import com.dididi.booking.social.domain.enums.MediaType;
import com.dididi.booking.social.repository.PostMediaRepository;
import com.dididi.booking.storage.StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Upload/serve anh + video cho mang xa hoi (qua MinIO). */
@Service
public class SocialMediaService {

    private static final String FOLDER_POSTS = "social/posts";
    private static final String FOLDER_AVATAR = "social/avatars";
    private static final int MAX_MEDIA = 10;
    private static final long MAX_IMAGE_BYTES = 25L * 1024 * 1024;  // 25MB (anh do phan giai cao)
    private static final long MAX_VIDEO_BYTES = 64L * 1024 * 1024;  // 64MB
    private static final Set<String> ALLOWED = Set.of("image/", "video/");

    private final StorageService storage;
    private final PostMediaRepository mediaRepository;

    public SocialMediaService(StorageService storage, PostMediaRepository mediaRepository) {
        this.storage = storage;
        this.mediaRepository = mediaRepository;
    }

    /** Luu cac file media cua 1 bai (anh + video), tra ve danh sach PostMedia da luu. */
    public List<PostMedia> attachPostMedia(Long postId, MultipartFile[] files) {
        List<PostMedia> out = new ArrayList<>();
        if (files == null) {
            return out;
        }
        int order = 0;
        for (MultipartFile f : files) {
            if (f == null || f.isEmpty()) {
                continue;
            }
            if (out.size() >= MAX_MEDIA) {
                break;
            }
            String ct = f.getContentType();
            boolean video = ct != null && ct.startsWith("video/");
            long max = video ? MAX_VIDEO_BYTES : MAX_IMAGE_BYTES;
            if (f.getSize() > max) {
                throw new BusinessException("MEDIA_TOO_LARGE",
                        "Tệp vượt quá dung lượng cho phép", HttpStatus.PAYLOAD_TOO_LARGE);
            }
            String key = storage.upload(f, FOLDER_POSTS, ALLOWED);
            PostMedia m = new PostMedia();
            m.setPostId(postId);
            m.setMediaType(video ? MediaType.VIDEO : MediaType.IMAGE);
            m.setObjectKey(key);
            m.setContentType(ct);
            m.setSortOrder(order++);
            out.add(mediaRepository.save(m));
        }
        return out;
    }

    /** Doc 1 media (theo id) ve bytes de serve. */
    public StorageService.StoredObject loadMedia(Long mediaId) {
        PostMedia m = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new BusinessException("MEDIA_NOT_FOUND", "Không tìm thấy media", HttpStatus.NOT_FOUND));
        return storage.load(m.getObjectKey());
    }

    /** Lay postId cua 1 media (de kiem tra quyen xem bai truoc khi serve). */
    public Long postIdOf(Long mediaId) {
        return mediaRepository.findById(mediaId)
                .orElseThrow(() -> new BusinessException("MEDIA_NOT_FOUND", "Không tìm thấy media", HttpStatus.NOT_FOUND))
                .getPostId();
    }

    /** Upload anh dai dien / anh bia (chi anh). */
    public String uploadAvatar(MultipartFile file) {
        return storage.upload(file, FOLDER_AVATAR);
    }

    /** Upload anh gui trong tin nhan (chi anh). */
    public String uploadMessageImage(MultipartFile file) {
        return storage.upload(file, "social/dm");
    }

    public StorageService.StoredObject loadByKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new BusinessException("MEDIA_NOT_FOUND", "Không tìm thấy media", HttpStatus.NOT_FOUND);
        }
        return storage.load(objectKey);
    }
}
