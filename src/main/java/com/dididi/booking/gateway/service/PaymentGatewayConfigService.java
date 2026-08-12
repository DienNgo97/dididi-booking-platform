package com.dididi.booking.gateway.service;

import com.dididi.booking.gateway.domain.PaymentGatewayConfig;
import com.dididi.booking.gateway.repository.PaymentGatewayConfigRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nguon cau hinh VNPay hieu luc: uu tien DB (neu Super Admin da luu), neu chua co thi
 * fallback ve application.yml (@Value). VnPayService doc qua service nay.
 */
@Service
public class PaymentGatewayConfigService {

    private static final String PROVIDER = "VNPAY";

    private final PaymentGatewayConfigRepository repository;

    @Value("${app.vnpay.tmn-code}")   private String fbTmnCode;
    @Value("${app.vnpay.hash-secret}") private String fbHashSecret;
    @Value("${app.vnpay.pay-url}")    private String fbPayUrl;
    /**
     * URL VNPay gọi ngược về sau thanh toán. Chuẩn hoá "//" -> "/" phòng khi PUBLIC_URL
     * được dán kèm dấu "/" ở cuối (vd https://abc.trycloudflare.com/ + /payment/vnpay-return).
     */
    private String fbReturnUrl;

    @Value("${app.vnpay.return-url}")
    void setFbReturnUrl(String v) {
        String s = (v == null) ? "" : v.trim();
        // gộp mọi "//" nằm SAU phần "scheme://" thành "/"
        int schemeEnd = s.indexOf("://");
        if (schemeEnd > 0) {
            String scheme = s.substring(0, schemeEnd + 3);
            String rest = s.substring(schemeEnd + 3).replaceAll("/{2,}", "/");
            s = scheme + rest;
        }
        this.fbReturnUrl = s;
        org.slf4j.LoggerFactory.getLogger(PaymentGatewayConfigService.class)
                .info("VNPay return-url (mặc định từ cấu hình) = {}", s);
    }

    public PaymentGatewayConfigService(PaymentGatewayConfigRepository repository) {
        this.repository = repository;
    }

    /** Cau hinh hien tai (DB neu co, nguoc lai 1 ban transient tu application.yml). */
    public PaymentGatewayConfig current() {
        return repository.findByProvider(PROVIDER).orElseGet(this::transientDefault);
    }

    private PaymentGatewayConfig transientDefault() {
        PaymentGatewayConfig c = new PaymentGatewayConfig();
        c.setProvider(PROVIDER);
        c.setTmnCode(fbTmnCode);
        c.setHashSecret(fbHashSecret);
        c.setPayUrl(fbPayUrl);
        c.setReturnUrl(fbReturnUrl);
        c.setEnabled(true);
        return c;
    }

    public String tmnCode()   { return orFb(current().getTmnCode(), fbTmnCode); }
    public String hashSecret(){ return orFb(current().getHashSecret(), fbHashSecret); }
    public String payUrl()    { return orFb(current().getPayUrl(), fbPayUrl); }
    public String returnUrl() { return orFb(current().getReturnUrl(), fbReturnUrl); }
    public boolean enabled()  { return current().isEnabled(); }

    @Transactional
    public PaymentGatewayConfig update(String tmnCode, String hashSecret, String payUrl,
                                       String returnUrl, Boolean enabled) {
        PaymentGatewayConfig c = repository.findByProvider(PROVIDER).orElseGet(() -> {
            PaymentGatewayConfig n = new PaymentGatewayConfig();
            n.setProvider(PROVIDER);
            n.setHashSecret(fbHashSecret);
            return n;
        });
        if (tmnCode != null) c.setTmnCode(tmnCode);
        if (payUrl != null) c.setPayUrl(payUrl);
        if (returnUrl != null) c.setReturnUrl(returnUrl);
        if (hashSecret != null && !hashSecret.isBlank()) c.setHashSecret(hashSecret);
        if (enabled != null) c.setEnabled(enabled);
        // dam bao khong rong -> fallback yaml
        if (isBlank(c.getTmnCode())) c.setTmnCode(fbTmnCode);
        if (isBlank(c.getPayUrl())) c.setPayUrl(fbPayUrl);
        if (isBlank(c.getReturnUrl())) c.setReturnUrl(fbReturnUrl);
        if (isBlank(c.getHashSecret())) c.setHashSecret(fbHashSecret);
        return repository.save(c);
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static String orFb(String v, String fb) { return isBlank(v) ? fb : v; }
}
