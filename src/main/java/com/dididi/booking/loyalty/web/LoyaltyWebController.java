package com.dididi.booking.loyalty.web;

import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.loyalty.service.LoyaltyService;
import com.dididi.booking.voucher.domain.Voucher;
import com.dididi.booking.web.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class LoyaltyWebController {

    private final LoyaltyService loyaltyService;
    private final CurrentUser currentUser;

    public LoyaltyWebController(LoyaltyService loyaltyService, CurrentUser currentUser) {
        this.loyaltyService = loyaltyService;
        this.currentUser = currentUser;
    }

    @GetMapping("/account/points")
    public String page(Authentication auth, Model model) {
        Long uid = currentUser.id(auth);
        model.addAttribute("balance", loyaltyService.balance(uid));
        model.addAttribute("tier", loyaltyService.tier(uid));
        model.addAttribute("lifetimeEarned", loyaltyService.lifetimeEarned(uid));
        model.addAttribute("history", loyaltyService.history(uid));
        model.addAttribute("redeemedVouchers", loyaltyService.redeemedVouchers(uid));
        model.addAttribute("redeemPointValue", loyaltyService.redeemPointValue());
        model.addAttribute("minRedeem", loyaltyService.minRedeem());
        return "account/points";
    }

    @PostMapping("/account/points/redeem")
    public String redeem(@RequestParam int points, Authentication auth, RedirectAttributes ra) {
        try {
            Voucher v = loyaltyService.redeemForVoucher(currentUser.id(auth), points);
            ra.addFlashAttribute("message", "Đã đổi " + points + " điểm. Mã giảm giá của bạn: " + v.getCode()
                    + " (giảm " + v.getDiscountValue().toBigInteger() + "đ, dùng 1 lần). Nhập mã này khi thanh toán.");
        } catch (BusinessException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/account/points";
    }
}
