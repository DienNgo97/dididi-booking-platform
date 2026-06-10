package com.dididi.booking.invite.web;

import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.corporate.domain.entity.Company;
import com.dididi.booking.corporate.service.CompanyService;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.invite.domain.CompanyInvite;
import com.dididi.booking.invite.domain.InviteStatus;
import com.dididi.booking.invite.service.CompanyInviteService;
import com.dididi.booking.web.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;

@Controller
public class InviteWebController {

    private final CompanyInviteService inviteService;
    private final CompanyService companyService;
    private final CurrentUser currentUser;

    public InviteWebController(CompanyInviteService inviteService, CompanyService companyService,
                               CurrentUser currentUser) {
        this.inviteService = inviteService;
        this.companyService = companyService;
        this.currentUser = currentUser;
    }

    @GetMapping("/company-invite/{token}")
    public String view(@PathVariable String token, Authentication auth, Model model) {
        User me = currentUser.require(auth);
        model.addAttribute("token", token);
        CompanyInvite i = inviteService.findByToken(token);
        if (i == null) {
            model.addAttribute("invalid", "Lời mời không tồn tại.");
            return "invite/accept";
        }
        Company c = companyService.getOrNull(i.getCompanyId());
        model.addAttribute("companyName", c != null ? c.getName() : ("#" + i.getCompanyId()));
        model.addAttribute("inviteEmail", i.getEmail());
        boolean pending = i.getStatus() == InviteStatus.PENDING;
        boolean expired = i.getExpiresAt().isBefore(Instant.now());
        boolean emailMatch = i.getEmail().equalsIgnoreCase(me.getEmail());
        if (!pending) {
            model.addAttribute("invalid", "Lời mời đã được dùng hoặc đã thu hồi.");
        } else if (expired) {
            model.addAttribute("invalid", "Lời mời đã hết hạn.");
        } else if (!emailMatch) {
            model.addAttribute("invalid", "Lời mời dành cho " + i.getEmail()
                    + ". Hãy đăng nhập bằng đúng email đó để chấp nhận.");
        } else {
            model.addAttribute("canAccept", true);
        }
        return "invite/accept";
    }

    @PostMapping("/company-invite/{token}/accept")
    public String accept(@PathVariable String token, Authentication auth, RedirectAttributes ra) {
        try {
            String company = inviteService.accept(token, currentUser.require(auth));
            ra.addFlashAttribute("message",
                    "Bạn đã tham gia công ty " + company + ". Giờ có thể thanh toán bằng ngân sách công ty.");
            return "redirect:/account/bookings";
        } catch (BusinessException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/company-invite/" + token;
        }
    }
}
