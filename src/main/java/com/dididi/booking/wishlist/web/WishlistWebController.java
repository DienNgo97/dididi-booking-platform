package com.dididi.booking.wishlist.web;

import com.dididi.booking.web.CurrentUser;
import com.dididi.booking.wishlist.service.WishlistService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class WishlistWebController {

    private final WishlistService wishlistService;
    private final CurrentUser currentUser;

    public WishlistWebController(WishlistService wishlistService, CurrentUser currentUser) {
        this.wishlistService = wishlistService;
        this.currentUser = currentUser;
    }

    @GetMapping("/account/wishlist")
    public String page(Authentication auth, Model model) {
        model.addAttribute("hotels", wishlistService.listHotels(currentUser.id(auth)));
        return "account/wishlist";
    }

    @PostMapping("/account/wishlist/toggle")
    public String toggle(@RequestParam Long hotelId,
                         @RequestParam(required = false) String back,
                         Authentication auth, RedirectAttributes ra) {
        boolean added = wishlistService.toggle(currentUser.id(auth), hotelId);
        ra.addFlashAttribute("message", added ? "Đã thêm vào yêu thích." : "Đã bỏ khỏi yêu thích.");
        return "redirect:" + safeBack(back);
    }

    /** Chi cho phep redirect ve duong dan noi bo (tranh open-redirect). */
    private static String safeBack(String back) {
        if (back != null && back.startsWith("/") && !back.startsWith("//")) return back;
        return "/account/wishlist";
    }
}
