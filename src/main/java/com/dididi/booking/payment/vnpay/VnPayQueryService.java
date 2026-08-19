package com.dididi.booking.payment.vnpay;

import com.dididi.booking.gateway.service.PaymentGatewayConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hỏi thẳng VNPay "giao dịch này kết thúc ra sao?" bằng API querydr.
 *
 * VÌ SAO CẦN: đơn chỉ được xác nhận khi VNPay báo về, mà có hai đường báo —
 *   1) trình duyệt khách quay lại (vnp_ReturnUrl): mất nếu khách tắt tab / rớt mạng ngay sau khi trả tiền
 *   2) IPN server-to-server: VNPay không gọi được nếu hệ thống chạy sau NAT / localhost
 * Cả hai đều có thể trượt. Đối soát chủ động là lớp thứ ba, do CHÍNH MÌNH gọi ra ngoài
 * nên không phụ thuộc việc ai gọi vào được — chạy ở localhost cũng hoạt động.
 *
 * Spec: https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr&refund.html
 * Lưu ý checksum của querydr KHÁC với lúc tạo URL thanh toán: không sort theo tên tham số
 * mà nối các trường theo ĐÚNG THỨ TỰ quy định, phân tách bằng dấu "|".
 */
@Service
public class VnPayQueryService {

    private static final Logger log = LoggerFactory.getLogger(VnPayQueryService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** Kết quả tra cứu. {@code ok} = gọi API thành công VÀ chữ ký phản hồi hợp lệ. */
    public record QueryResult(boolean ok, String responseCode, String transactionStatus,
                              BigDecimal amount, String transactionNo, String bankCode,
                              String payDate, String message) {

        /** Giao dịch đã thanh toán thành công (vnp_TransactionStatus = 00). */
        public boolean paid() { return ok && "00".equals(transactionStatus); }

        /** Giao dịch hỏng hẳn, sẽ không bao giờ thành công (02 lỗi, 07 nghi ngờ gian lận, 09 từ chối). */
        public boolean failed() {
            return ok && ("02".equals(transactionStatus) || "07".equals(transactionStatus)
                    || "09".equals(transactionStatus));
        }

        /** VNPay không tìm thấy giao dịch: khách chưa từng bấm trả tiền. */
        public boolean notFound() { return ok && "91".equals(responseCode); }
    }

    private final PaymentGatewayConfigService gateway;
    private final RestClient client;
    private final String version;

    public VnPayQueryService(PaymentGatewayConfigService gateway,
                             @Value("${app.vnpay.api-url:https://sandbox.vnpayment.vn/merchant_webapi/api/transaction}") String apiUrl,
                             @Value("${app.vnpay.version:2.1.0}") String version) {
        this.gateway = gateway;
        this.version = version;
        this.client = RestClient.builder().baseUrl(apiUrl).build();
    }

    /**
     * @param txnRef          vnp_TxnRef đã gửi lúc tạo giao dịch
     * @param transactionDate vnp_CreateDate lúc tạo giao dịch (yyyyMMddHHmmss)
     * @param clientIp        IP máy chủ gọi API
     */
    public QueryResult query(String txnRef, String transactionDate, String clientIp) {
        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
        String createDate = LocalDateTime.now(ZONE).format(FMT);
        String ip = VnPayService.normalizeIpv4(clientIp);
        String orderInfo = "Truy van GD " + txnRef;
        String tmnCode = gateway.tmnCode();

        // Thứ tự nối CỐ ĐỊNH theo tài liệu — sai thứ tự là checksum sai (mã lỗi 97).
        String data = String.join("|", requestId, version, "querydr", tmnCode, txnRef,
                transactionDate, createDate, ip, orderInfo);
        String checksum = VnPayUtil.hmacSHA512(gateway.hashSecret(), data);

        Map<String, String> body = new LinkedHashMap<>();
        body.put("vnp_RequestId", requestId);
        body.put("vnp_Version", version);
        body.put("vnp_Command", "querydr");
        body.put("vnp_TmnCode", tmnCode);
        body.put("vnp_TxnRef", txnRef);
        body.put("vnp_OrderInfo", orderInfo);
        body.put("vnp_TransactionDate", transactionDate);
        body.put("vnp_CreateDate", createDate);
        body.put("vnp_IpAddr", ip);
        body.put("vnp_SecureHash", checksum);

        Map<?, ?> res;
        try {
            res = client.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            // Cổng lỗi/timeout KHÔNG được làm hỏng luồng gọi — lần đối soát sau thử lại.
            log.warn("[VNPay querydr] gọi API thất bại cho {}: {}", txnRef, e.toString());
            return new QueryResult(false, null, null, null, null, null, null, e.toString());
        }
        if (res == null) {
            return new QueryResult(false, null, null, null, null, null, null, "phản hồi rỗng");
        }

        String responseCode = str(res.get("vnp_ResponseCode"));
        String txnStatus = str(res.get("vnp_TransactionStatus"));

        // Phản hồi LỖI của cổng (94 trùng yêu cầu, 02 sai TmnCode, 03 sai định dạng, 97 sai checksum…)
        // chỉ có vnp_ResponseCode + vnp_Message, KHÔNG kèm chữ ký — không có gì để verify.
        // Đây không phải sự cố dữ liệu, chỉ là "lần này chưa hỏi được" -> log gọn, thử lại sau.
        if (res.get("vnp_SecureHash") == null) {
            log.warn("[VNPay querydr] cổng từ chối truy vấn {}: {} — {} (sẽ hỏi lại sau)",
                    txnRef, responseCode, str(res.get("vnp_Message")));
            return new QueryResult(false, responseCode, txnStatus, null, null, null, null,
                    str(res.get("vnp_Message")));
        }

        if (!verify(res)) {
            // Chữ ký sai => KHÔNG tin phản hồi, dù nội dung nói gì. Coi như chưa biết kết quả.
            // In kèm mã + thông điệp + danh sách trường VNPay thực trả về: khi cổng đổi định dạng
            // (thêm/bớt trường) thì đây là manh mối duy nhất để sửa lại công thức checksum.
            log.error("[VNPay querydr] CHỮ KÝ PHẢN HỒI KHÔNG HỢP LỆ cho {} — responseCode={} message={} · các trường nhận được: {}",
                    txnRef, responseCode, str(res.get("vnp_Message")), new java.util.TreeSet<>(
                            res.keySet().stream().map(String::valueOf).toList()));
            return new QueryResult(false, responseCode, txnStatus, null, null, null, null, "chữ ký không hợp lệ");
        }

        BigDecimal amount = null;
        try {
            String raw = str(res.get("vnp_Amount"));
            if (raw != null && !raw.isBlank()) amount = new BigDecimal(raw).movePointLeft(2);
        } catch (Exception ignore) { /* số tiền hỏng -> để null, caller sẽ không xác nhận */ }

        return new QueryResult(true, responseCode, txnStatus, amount,
                str(res.get("vnp_TransactionNo")), str(res.get("vnp_BankCode")),
                str(res.get("vnp_PayDate")), str(res.get("vnp_Message")));
    }

    /**
     * Kiểm chữ ký phản hồi. Thứ tự nối do tài liệu quy định, KHÁC chiều gửi đi.
     *
     * Tài liệu liệt kê đủ 15 trường, nhưng thực tế cổng chỉ trả về một phần khi giao dịch
     * chưa hoàn tất hoặc khi có lỗi (không có vnp_PayDate, vnp_PromotionCode…). Không có
     * quy định rõ trường thiếu thì tính là chuỗi rỗng hay bị loại khỏi công thức, nên thử
     * cả hai cách rồi mới kết luận là sai — thà thử thêm một phép băm còn hơn từ chối nhầm
     * một phản hồi hợp lệ (từ chối nhầm nghĩa là đơn đã trả tiền không bao giờ được xác nhận).
     */
    private boolean verify(Map<?, ?> res) {
        String received = str(res.get("vnp_SecureHash"));
        if (received == null || received.isBlank()) return false;

        String[] order = {"vnp_ResponseId", "vnp_Command", "vnp_ResponseCode", "vnp_Message",
                "vnp_TmnCode", "vnp_TxnRef", "vnp_Amount", "vnp_BankCode", "vnp_PayDate",
                "vnp_TransactionNo", "vnp_TransactionType", "vnp_TransactionStatus",
                "vnp_OrderInfo", "vnp_PromotionCode", "vnp_PromotionAmount"};

        StringBuilder withEmpty = new StringBuilder();   // trường thiếu -> chuỗi rỗng
        StringBuilder skipMissing = new StringBuilder(); // trường thiếu -> bỏ hẳn khỏi công thức
        for (String k : order) {
            Object v = res.get(k);
            if (withEmpty.length() > 0) withEmpty.append('|');
            withEmpty.append(nz(v));
            if (v != null && !String.valueOf(v).isEmpty()) {
                if (skipMissing.length() > 0) skipMissing.append('|');
                skipMissing.append(v);
            }
        }
        return matches(withEmpty.toString(), received) || matches(skipMissing.toString(), received);
    }

    private boolean matches(String data, String received) {
        String expected = VnPayUtil.hmacSHA512(gateway.hashSecret(), data);
        return java.security.MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                received.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    private static String nz(Object o) { return o == null ? "" : String.valueOf(o); }
}
