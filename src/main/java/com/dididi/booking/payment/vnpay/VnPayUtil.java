package com.dididi.booking.payment.vnpay;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Tien ich ky/verify chu ky VNPay (HMAC-SHA512), theo dung mau code mau chinh thuc cua VNPay 2.1.0.
 * Quan trong: hashData phai duoc build tu cac field SAP XEP theo ten (alphabet) va
 * gia tri duoc URL-encode bang US-ASCII. Ca luc tao URL lan luc verify deu dung CUNG mot ham nay.
 */
public final class VnPayUtil {

    private VnPayUtil() {}

    /** HMAC-SHA512(key, data) -> hex string thuong. */
    public static String hmacSHA512(String key, String data) {
        try {
            if (key == null || data == null) {
                throw new IllegalArgumentException("key/data null khi ky HMAC");
            }
            Mac hmac = Mac.getInstance("HmacSHA512");
            hmac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] bytes = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(2 * bytes.length);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit((b & 0xF), 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Khong the tinh HMAC-SHA512", e);
        }
    }

    /**
     * Build chuoi hashData tu map field (bo qua gia tri rong), sap xep theo ten field,
     * value URL-encode US-ASCII, noi bang dau '&'. KHONG bao gom vnp_SecureHash / vnp_SecureHashType.
     */
    public static String buildHashData(Map<String, String> fields) {
        TreeMap<String, String> sorted = new TreeMap<>(fields);
        sorted.remove("vnp_SecureHash");
        sorted.remove("vnp_SecureHashType");
        StringBuilder hashData = new StringBuilder();
        List<String> names = sorted.keySet().stream().toList();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            String value = sorted.get(name);
            if (value == null || value.isEmpty()) continue;
            if (hashData.length() > 0) hashData.append('&');
            hashData.append(name).append('=')
                    .append(URLEncoder.encode(value, StandardCharsets.US_ASCII));
        }
        return hashData.toString();
    }

    /** Ky toan bo field bang secret -> chu ky hex. */
    public static String signFields(Map<String, String> fields, String secret) {
        return hmacSHA512(secret, buildHashData(fields));
    }

    /** Verify chu ky tra ve tu VNPay. So sanh khong phan biet hoa thuong. */
    public static boolean isValidSignature(Map<String, String> fields, String secret) {
        String received = fields.get("vnp_SecureHash");
        if (received == null || received.isEmpty()) return false;
        String expected = signFields(fields, secret);
        // So sanh HANG-THOI-GIAN (constant-time) tranh timing-oracle tren HMAC (thay cho equalsIgnoreCase).
        return java.security.MessageDigest.isEqual(
                expected.toLowerCase().getBytes(StandardCharsets.UTF_8),
                received.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }
}
