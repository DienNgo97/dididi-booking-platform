package com.dididi.booking.ops.service;

import com.dididi.booking.ops.domain.OpsAlert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * P1-12 — SỨC KHOẺ JOB NỀN.
 *
 * <p>Trước đây mọi job đều bắt ngoại lệ rồi {@code log.warn(...)} và đi tiếp. Đối soát VNPay hỏng,
 * đồng bộ PMS hỏng, ghi sổ ví hỏng nhiều ngày liền cũng không ai biết — log chỉ được đọc khi đã có
 * người phàn nàn. Nay đếm số lần hỏng LIÊN TIẾP của từng job; quá ngưỡng thì mở cảnh báo vận hành,
 * chạy lại thành công thì tự đóng.</p>
 */
@Service
public class JobHealthService {

    private static final Logger log = LoggerFactory.getLogger(JobHealthService.class);

    /** Hỏng liên tiếp bấy nhiêu lần thì gọi người — 1 lần lỗi mạng lẻ tẻ là chuyện thường. */
    private static final int NGUONG_BAO_DONG = 3;

    private final OpsAlertService alerts;
    private final Map<String, AtomicInteger> hongLienTiep = new ConcurrentHashMap<>();

    public JobHealthService(OpsAlertService alerts) {
        this.alerts = alerts;
    }

    /** Job chạy xong êm -> xoá bộ đếm và đóng cảnh báo nếu đang mở. */
    public void thanhCong(String job) {
        AtomicInteger c = hongLienTiep.get(job);
        if (c != null && c.getAndSet(0) >= NGUONG_BAO_DONG) {
            alerts.autoResolveByKey(OpsAlert.Type.JOB_FAILING, "JOB:" + job, "Job đã chạy lại bình thường");
            log.info("[ops] Job {} đã hồi phục.", job);
        }
    }

    /** Job hỏng -> đếm; quá ngưỡng thì mở cảnh báo cho người thật. */
    public void thatBai(String job, Throwable ex) {
        int lan = hongLienTiep.computeIfAbsent(job, k -> new AtomicInteger()).incrementAndGet();
        log.error("[ops] Job {} hỏng lần thứ {} liên tiếp: {}", job, lan, ex == null ? "?" : ex.toString());
        if (lan == NGUONG_BAO_DONG || lan % (NGUONG_BAO_DONG * 5) == 0) {
            alerts.raise(OpsAlert.Type.JOB_FAILING, OpsAlert.Severity.CRITICAL, null, "JOB:" + job,
                    "Job nền '" + job + "' hỏng " + lan + " lần liên tiếp — lỗi mới nhất: "
                            + (ex == null ? "không rõ" : ex.toString()),
                    "Xem log của job này. Job hỏng lâu có nghĩa là: đối soát/ghi sổ/đồng bộ đang KHÔNG chạy, "
                            + "số liệu sẽ lệch dần cho tới khi sửa xong.");
        }
    }
}
