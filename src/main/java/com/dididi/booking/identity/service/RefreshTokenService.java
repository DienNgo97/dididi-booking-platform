package com.dididi.booking.identity.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Quan ly refresh token bang Redis (Phase Dot1 #3).
 *  - issue: sinh token ngau nhien, luu Redis key "refresh:{token}" -> userId, het han sau N ngay.
 *           dong thoi them vao set "refresh:user:{userId}" de co the thu hoi tat ca token cua 1 user.
 *  - userIdOf: tra userId neu token con hieu luc (chua het han / chua bi thu hoi).
 *  - revoke: xoa token (dung khi logout).
 *  - rotate: thu hoi token cu + phat token moi (chong dung lai refresh token da xai).
 *  - revokeAllForUser: thu hoi MOI refresh token cua user (dang xuat moi thiet bi).
 *  - invalidateAccessTokensBefore / isAccessTokenStillValid: moc vo hieu access token JWT cu
 *    (dung khi doi mat khau de tat tat ca phien dang nhap ngay lap tuc).
 */
@Service
public class RefreshTokenService {

    private static final String PREFIX = "refresh:";
    private static final String USER_INDEX = "refresh:user:";   // set: userId -> {tokens}
    private static final String PW_CHANGED = "pwchanged:";       // userId -> epoch giay (moc vo hieu access token cu)
    private static final String DEV_META = "refreshdev:";        // token -> "device\nepochGiay" (metadata phien/thiet bi)
    private static final SecureRandom RND = new SecureRandom();

    private final StringRedisTemplate redis;
    private final long refreshDays;
    private final long accessTokenMinutes;

    public RefreshTokenService(StringRedisTemplate redis,
                               @Value("${app.jwt.refresh-token-days:7}") long refreshDays,
                               @Value("${app.jwt.access-token-minutes:60}") long accessTokenMinutes) {
        this.redis = redis;
        this.refreshDays = refreshDays;
        this.accessTokenMinutes = accessTokenMinutes;
    }

    public String issue(Long userId) {
        return issue(userId, currentDeviceLabel());
    }

    /** Phat refresh token kem nhan thiet bi (metadata phien) de liet ke/thu hoi tung phien rieng le. */
    public String issue(Long userId, String device) {
        byte[] buf = new byte[32];
        RND.nextBytes(buf);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        redis.opsForValue().set(PREFIX + token, String.valueOf(userId), Duration.ofDays(refreshDays));
        // index nguoc: de thu hoi tat ca token cua 1 user
        String idxKey = USER_INDEX + userId;
        redis.opsForSet().add(idxKey, token);
        redis.expire(idxKey, Duration.ofDays(refreshDays));
        // metadata phien: nhan thiet bi + moc tao (de liet ke o "Thiet bi dang nhap")
        String label = (device == null || device.isBlank()) ? "Thiết bị" : device;
        redis.opsForValue().set(DEV_META + token, label + "\n" + Instant.now().getEpochSecond(),
                Duration.ofDays(refreshDays));
        return token;
    }

    public Long userIdOf(String token) {
        if (token == null || token.isBlank()) return null;
        String v = redis.opsForValue().get(PREFIX + token);
        if (v == null) return null;
        try {
            return Long.valueOf(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void revoke(String token) {
        if (token == null || token.isBlank()) return;
        Long userId = userIdOf(token);
        redis.delete(PREFIX + token);
        redis.delete(DEV_META + token);
        if (userId != null) {
            redis.opsForSet().remove(USER_INDEX + userId, token);
        }
    }

    public String rotate(Long userId, String oldToken) {
        String device = deviceLabelOf(oldToken);   // giu nhan thiet bi qua vong xoay token
        revoke(oldToken);
        return issue(userId, device);
    }

    /** Thu hoi TAT CA refresh token cua 1 user (dang xuat moi thiet bi dung API). */
    public void revokeAllForUser(Long userId) {
        if (userId == null) return;
        String idxKey = USER_INDEX + userId;
        Set<String> tokens = redis.opsForSet().members(idxKey);
        if (tokens != null) {
            for (String t : tokens) {
                redis.delete(PREFIX + t);
            }
        }
        redis.delete(idxKey);
    }

    /** Dat moc: moi access token (JWT) phat hanh TRUOC thoi diem nay deu bi coi la het hieu luc. */
    public void invalidateAccessTokensBefore(Long userId) {
        if (userId == null) return;
        long nowSec = Instant.now().getEpochSecond();
        // chi can giu toi khi token cu chac chan da het han
        redis.opsForValue().set(PW_CHANGED + userId, String.valueOf(nowSec),
                Duration.ofMinutes(accessTokenMinutes + 1));
    }

    /** Access token con hieu luc khong (so iat voi moc doi mat khau). */
    public boolean isAccessTokenStillValid(String userId, long issuedAtEpochSeconds) {
        if (userId == null) return true;
        String v = redis.opsForValue().get(PW_CHANGED + userId);
        if (v == null) return true;
        try {
            return issuedAtEpochSeconds >= Long.parseLong(v);
        } catch (NumberFormatException e) {
            return true;
        }
    }

    public long refreshDays() {
        return refreshDays;
    }

    // ───────────────────────── Danh sách thiết bị / phiên ─────────────────────────

    /** Một phiên đăng nhập (thiết bị). id = mã băm của refresh token (KHÔNG lộ token thật ra ngoài). */
    public record SessionInfo(String id, String device, long createdAtEpoch) {}

    /** Liệt kê các phiên (thiết bị) đang hoạt động của user, mới nhất trước. */
    public List<SessionInfo> listSessions(Long userId) {
        List<SessionInfo> out = new ArrayList<>();
        if (userId == null) return out;
        Set<String> tokens = redis.opsForSet().members(USER_INDEX + userId);
        if (tokens != null) {
            for (String t : tokens) {
                if (redis.opsForValue().get(PREFIX + t) == null) {
                    redis.opsForSet().remove(USER_INDEX + userId, t); // dọn token hết hạn còn kẹt trong index
                    continue;
                }
                String meta = redis.opsForValue().get(DEV_META + t);
                String device = "Thiết bị";
                long created = 0;
                if (meta != null) {
                    int nl = meta.indexOf('\n');
                    if (nl >= 0) {
                        device = meta.substring(0, nl);
                        try { created = Long.parseLong(meta.substring(nl + 1).trim()); } catch (NumberFormatException ignored) {}
                    } else {
                        device = meta;
                    }
                }
                out.add(new SessionInfo(sid(t), device, created));
            }
        }
        out.sort(Comparator.comparingLong(SessionInfo::createdAtEpoch).reversed());
        return out;
    }

    /** Thu hồi 1 phiên theo id (mã băm token). Trả true nếu tìm thấy & thu hồi. */
    public boolean revokeBySessionId(Long userId, String sessionId) {
        if (userId == null || sessionId == null) return false;
        Set<String> tokens = redis.opsForSet().members(USER_INDEX + userId);
        if (tokens != null) {
            for (String t : tokens) {
                if (sid(t).equals(sessionId)) {
                    revoke(t);
                    return true;
                }
            }
        }
        return false;
    }

    /** Mã băm (id phiên) của 1 refresh token — dùng để đánh dấu phiên hiện tại. */
    public String sessionIdOf(String token) {
        return token == null ? null : sid(token);
    }

    private String deviceLabelOf(String token) {
        if (token == null) return null;
        String meta = redis.opsForValue().get(DEV_META + token);
        if (meta == null) return null;
        int nl = meta.indexOf('\n');
        return nl >= 0 ? meta.substring(0, nl) : meta;
    }

    private static String sid(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8 && i < h.length; i++) sb.append(String.format("%02x", h[i]));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(token.hashCode());
        }
    }

    /** Nhãn thiết bị suy từ User-Agent của request hiện tại (nếu có ngữ cảnh servlet). */
    private String currentDeviceLabel() {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                return labelFromUserAgent(sra.getRequest().getHeader("User-Agent"));
            }
        } catch (Exception ignored) {}
        return "Thiết bị";
    }

    private static String labelFromUserAgent(String ua) {
        if (ua == null || ua.isBlank()) return "Thiết bị";
        String s = ua.toLowerCase();
        String os = s.contains("android") ? "Android"
                : (s.contains("iphone") || s.contains("ipad") || s.contains("ios")) ? "iOS"
                : s.contains("windows") ? "Windows"
                : (s.contains("mac os") || s.contains("macintosh")) ? "macOS"
                : s.contains("linux") ? "Linux" : "Thiết bị";
        String app = (s.contains("dart") || s.contains("flutter")) ? "Ứng dụng Dididi"
                : s.contains("edg") ? "Edge"
                : s.contains("chrome") ? "Chrome"
                : s.contains("firefox") ? "Firefox"
                : s.contains("safari") ? "Safari" : "Trình duyệt";
        return app + " · " + os;
    }
}
