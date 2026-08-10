package com.dididi.booking.promo.web;

import com.dididi.booking.promo.service.PersonalPromoService;
import com.dididi.booking.voucher.domain.Voucher;
import com.dididi.booking.web.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * "Ưu đãi của tôi" — nơi khách xem các voucher RIÊNG được tặng (sinh nhật, khách quay lại,
 * tri ân hạng, chào mừng) và voucher đổi từ điểm. Trước đây voucher tặng không hiện ở đâu cả.
 */
@Controller
public class OfferWebController {

    private final PersonalPromoService promoService;
    private final CurrentUser currentUser;

    public OfferWebController(PersonalPromoService promoService, CurrentUser currentUser) {
        this.promoService = promoService;
        this.currentUser = currentUser;
    }

    @GetMapping("/account/offers")
    public String offers(Authentication auth, Model model) {
        Long uid = currentUser.id(auth);
        Instant now = Instant.now();
        List<Voucher> usable = new ArrayList<>();
        List<Voucher> expired = new ArrayList<>();
        for (Voucher v : promoService.myVouchers(uid)) {
            boolean dead = !v.isActive()
                    || (v.getValidTo() != null && v.getValidTo().isBefore(now))
                    || (v.getValidFrom() != null && v.getValidFrom().isAfter(now));
            (dead ? expired : usable).add(v);
        }
        model.addAttribute("usable", usable);
        model.addAttribute("expired", expired);
        model.addAttribute("grants", promoService.myGrants(uid));
        return "account/offers";
    }
}
