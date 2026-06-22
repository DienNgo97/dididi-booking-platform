package com.dididi.booking.identity.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Trang tĩnh: Điều khoản sử dụng & Chính sách bảo mật của Dididi (công khai). */
@Controller
public class LegalWebController {

    @GetMapping("/terms")
    public String terms() {
        return "legal/terms";
    }

    @GetMapping("/privacy")
    public String privacy() {
        return "legal/privacy";
    }
}
