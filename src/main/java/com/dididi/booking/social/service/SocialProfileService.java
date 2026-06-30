package com.dididi.booking.social.service;

import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.repository.UserRepository;
import com.dididi.booking.social.domain.entity.SocialProfile;
import com.dididi.booking.social.domain.enums.ProfileVisibility;
import com.dididi.booking.social.repository.SocialProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;

/** Quan ly ho so mang xa hoi: tao lazy, doi handle/ten/bio, anh dai dien, quyen rieng tu. */
@Service
@Transactional
public class SocialProfileService {

    private final SocialProfileRepository profileRepository;
    private final UserRepository userRepository;

    public SocialProfileService(SocialProfileRepository profileRepository, UserRepository userRepository) {
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
    }

    /** Lay ho so cua user, tao moi (handle mac dinh) neu chua co. */
    public SocialProfile getOrCreate(Long userId) {
        return profileRepository.findByUserId(userId).orElseGet(() -> {
            User u = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException("NO_USER", "Không tìm thấy người dùng", HttpStatus.NOT_FOUND));
            SocialProfile p = new SocialProfile();
            p.setUserId(userId);
            p.setDisplayName(u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : "Thành viên Dididi");
            p.setHandle(generateUniqueHandle(baseHandle(u)));
            p.setVisibility(ProfileVisibility.PUBLIC);
            return profileRepository.save(p);
        });
    }

    @Transactional(readOnly = true)
    public Optional<SocialProfile> findByHandle(String handle) {
        return handle == null ? Optional.empty() : profileRepository.findByHandle(handle.trim().toLowerCase(Locale.ROOT));
    }

    @Transactional(readOnly = true)
    public Optional<SocialProfile> findByUserId(Long userId) {
        return profileRepository.findByUserId(userId);
    }

    public SocialProfile updateProfile(Long userId, String displayName, String bio, String link, boolean isPrivate) {
        SocialProfile p = getOrCreate(userId);
        if (displayName != null) {
            p.setDisplayName(trim(displayName, 120));
        }
        p.setBio(trim(bio, 500));
        p.setLink(safeLink(trim(link, 200)));
        p.setVisibility(isPrivate ? ProfileVisibility.PRIVATE : ProfileVisibility.PUBLIC);
        return profileRepository.save(p);
    }

    /**
     * Chi chap nhan link http/https (chong XSS qua scheme javascript:/data:/vbscript: trong th:href).
     * Khong co scheme -> tu them https://. Co scheme khac -> bo (tra null).
     */
    private static String safeLink(String link) {
        if (link == null || link.isBlank()) {
            return null;
        }
        String l = link.trim();
        String low = l.toLowerCase(Locale.ROOT);
        if (low.startsWith("http://") || low.startsWith("https://")) {
            return l;
        }
        if (low.matches("^[a-z][a-z0-9+.\\-]*:.*")) {
            return null; // co scheme nguy hiem khac (javascript:, data:, ...) -> loai bo
        }
        return "https://" + l;
    }

    public SocialProfile changeHandle(Long userId, String newHandle) {
        String h = newHandle == null ? "" : newHandle.trim().toLowerCase(Locale.ROOT);
        if (!h.matches("^[a-z0-9_]{3,30}$")) {
            throw new BusinessException("BAD_HANDLE",
                    "Tên định danh chỉ gồm chữ thường, số, gạch dưới (3-30 ký tự)", HttpStatus.BAD_REQUEST);
        }
        SocialProfile p = getOrCreate(userId);
        if (h.equals(p.getHandle())) {
            return p;
        }
        if (profileRepository.existsByHandle(h)) {
            throw new BusinessException("HANDLE_TAKEN", "Tên định danh đã có người dùng", HttpStatus.CONFLICT);
        }
        p.setHandle(h);
        return profileRepository.save(p);
    }

    public void setAvatarKey(Long userId, String key) {
        SocialProfile p = getOrCreate(userId);
        p.setAvatarKey(key);
        profileRepository.save(p);
    }

    public void setCoverKey(Long userId, String key) {
        SocialProfile p = getOrCreate(userId);
        p.setCoverKey(key);
        profileRepository.save(p);
    }

    public void adjustPostsCount(Long userId, int delta) {
        profileRepository.findByUserId(userId).ifPresent(p -> {
            p.setPostsCount(Math.max(0, p.getPostsCount() + delta));
            profileRepository.save(p);
        });
    }

    // ---- handle helpers ----

    private String baseHandle(User u) {
        String src = u.getFullName();
        if (src == null || src.isBlank()) {
            String email = u.getEmail();
            src = email != null && email.contains("@") ? email.substring(0, email.indexOf('@')) : "user";
        }
        String norm = Normalizer.normalize(src, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('đ', 'd').replace('Đ', 'd')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]", "");
        if (norm.length() < 3) {
            norm = "user" + norm;
        }
        if (norm.length() > 24) {
            norm = norm.substring(0, 24);
        }
        return norm;
    }

    private String generateUniqueHandle(String base) {
        if (!profileRepository.existsByHandle(base)) {
            return base;
        }
        for (int i = 1; i < 10000; i++) {
            String candidate = base + i;
            if (!profileRepository.existsByHandle(candidate)) {
                return candidate;
            }
        }
        return base + System.currentTimeMillis();
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
