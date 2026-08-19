package com.dididi.booking.identity.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Trang tĩnh: Điều khoản sử dụng & Chính sách bảo mật của Dididi (công khai). */
@Controller
public class LegalWebController {

    @GetMapping({"/terms", "/legal/terms"})   // footer cu tro /legal/... -> alias de khong 404
    public String terms() {
        return "legal/terms";
    }

    @GetMapping({"/privacy", "/legal/privacy"})
    public String privacy() {
        return "legal/privacy";
    }
}
