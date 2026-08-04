package com.dididi.booking.notification.service;

import com.dididi.booking.notification.domain.DeviceToken;
import com.dididi.booking.notification.repository.DeviceTokenRepository;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Gửi push FCM tới mọi thiết bị của 1 user. Chạy @Async để không chặn luồng nghiệp vụ.
 * Nếu Firebase chưa bật (không có FirebaseApp bean) -> bỏ qua im lặng.
 */
@Service
public class PushSender {

    private static final Logger log = LoggerFactory.getLogger(PushSender.class);

    private final DeviceTokenRepository tokenRepo;
    private final ObjectProvider<FirebaseApp> firebaseApp;

    public PushSender(DeviceTokenRepository tokenRepo, ObjectProvider<FirebaseApp> firebaseApp) {
        this.tokenRepo = tokenRepo;
        this.firebaseApp = firebaseApp;
    }

    /** Gửi 1 push thử nghiệm ĐỒNG BỘ + trả về kết quả từng token (để chẩn đoán). */
    public List<String> sendTest(Long userId) {
        List<String> out = new ArrayList<>();
        if (firebaseApp.getIfAvailable() == null) {
            out.add("firebase_disabled (app.firebase.enabled=false hoặc FirebaseApp init lỗi)");
            return out;
        }
        List<DeviceToken> toks = tokenRepo.findByUserId(userId);
        out.add("tokenCount=" + toks.size());
        for (DeviceToken t : toks) {
            try {
                String id = FirebaseMessaging.getInstance().send(Message.builder()
                        .setToken(t.getToken())
                        .setNotification(Notification.builder()
                                .setTitle("Dididi")
                                .setBody("Thông báo thử nghiệm 🎉")
                                .build())
                        .build());
                out.add("OK token#" + t.getId() + " (" + t.getPlatform() + ") -> " + id);
            } catch (Exception e) {
                out.add("ERR token#" + t.getId() + " -> " + e.getMessage());
            }
        }
        return out;
    }

    @Async
    public void sendToUser(Long userId, String title, String body, String url) {
        if (userId == null || firebaseApp.getIfAvailable() == null) {
            return; // push chưa bật
        }
        for (DeviceToken t : tokenRepo.findByUserId(userId)) {
            try {
                Message msg = Message.builder()
                        .setToken(t.getToken())
                        .setNotification(Notification.builder()
                                .setTitle(title == null ? "Dididi" : title)
                                .setBody(body == null ? "" : body)
                                .build())
                        .putData("url", url == null ? "" : url)
                        .build();
                FirebaseMessaging.getInstance().send(msg);
            } catch (FirebaseMessagingException e) {
                MessagingErrorCode code = e.getMessagingErrorCode();
                log.warn("FCM gửi lỗi token#{} ({}): {}", t.getId(), code, e.getMessage());
                // Token không còn hợp lệ -> xoá để lần sau không gửi nữa.
                if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                    try {
                        tokenRepo.delete(t);
                    } catch (RuntimeException ignore) {
                        // ignore
                    }
                }
            } catch (Exception e) {
                log.warn("FCM lỗi không xác định: {}", e.toString());
            }
        }
    }
}
