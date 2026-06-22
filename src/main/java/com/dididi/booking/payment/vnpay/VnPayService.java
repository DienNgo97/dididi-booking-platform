package com.dididi.booking.payment.vnpay;

import com.dididi.booking.booking.domain.entity.Booking;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;

/**
 * Tao URL thanh toan VNPay (sandbox) va verify chu ky tra ve.
 * Cau hinh trong application.yml duoi prefix app.vnpay.
 */
@Service
public class VnPayService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Value("${app.vnpay.version:2.1.0}")   private String version;
    @Value("${app.vnpay.command:pay}")     private String command;
    @Value("${app.vnpay.order-type:other}")private String orderType;
    @Value("${app.vnpay.locale:vn}")       private String locale;

    private final com.dididi.booking.gateway.service.PaymentGatewayConfigService gateway;

    public VnPayService(com.dididi.booking.gateway.service.PaymentGatewayConfigService gateway) {
        this.gateway = gateway;
    }

    /**
     * Build URL chuyen huong sang VNPay cho 1 don hang.
     * @param booking don can thanh toan (lay amount/currency)
     * @param txnRef   ma giao dich duy nhat (vnp_TxnRef), vd publicCode + "_" + timestamp
     * @param clientIp IP nguoi dat
     */
    public String createPaymentUrl(Booking booking, String txnRef, String clientIp) {
        return createPaymentUrl(booking.getAmount(), txnRef,
                "Thanh toan don hang " + booking.getPublicCode(), clientIp);
    }

    /** Tao URL VNPay voi so tien + noi dung tuy y (dung cho thanh toan gop ca nhom). */
    public String createPaymentUrl(BigDecimal amount, String txnRef, String orderInfo, String clientIp) {
        LocalDateTime now = LocalDateTime.now(ZONE);
        // VNPay: amount x 100, khong thap phan
        String vnpAmount = amount
                .multiply(BigDecimal.valueOf(100)).toBigInteger().toString();

        Map<String, String> p = new TreeMap<>();
        p.put("vnp_Version", version);
        p.put("vnp_Command", command);
        p.put("vnp_TmnCode", gateway.tmnCode());
        p.put("vnp_Amount", vnpAmount);
        p.put("vnp_CurrCode", "VND");
        p.put("vnp_TxnRef", txnRef);
        p.put("vnp_OrderInfo", orderInfo);
        p.put("vnp_OrderType", orderType);
        p.put("vnp_Locale", (locale == null || locale.isBlank()) ? "vn" : locale);
        p.put("vnp_ReturnUrl", gateway.returnUrl());
        p.put("vnp_IpAddr", (clientIp == null || clientIp.isBlank()) ? "127.0.0.1" : clientIp);
        p.put("vnp_CreateDate", now.format(FMT));
        p.put("vnp_ExpireDate", now.plusMinutes(15).format(FMT));

        // Build query (ten + gia tri deu URL-encode US-ASCII) song song voi hashData
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> e : p.entrySet()) {
            String value = e.getValue();
            if (value == null || value.isEmpty()) continue;
            if (query.length() > 0) query.append('&');
            query.append(URLEncoder.encode(e.getKey(), StandardCharsets.US_ASCII))
                 .append('=')
                 .append(URLEncoder.encode(value, StandardCharsets.US_ASCII));
        }
        String secureHash = VnPayUtil.signFields(p, gateway.hashSecret());
        return gateway.payUrl() + "?" + query + "&vnp_SecureHash=" + secureHash;
    }

    /** Verify chu ky cua tham so VNPay tra ve (return URL hoac IPN). */
    public boolean isValid(Map<String, String> params) {
        return VnPayUtil.isValidSignature(params, gateway.hashSecret());
    }
}
