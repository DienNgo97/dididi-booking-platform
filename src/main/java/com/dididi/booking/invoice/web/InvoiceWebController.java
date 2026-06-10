package com.dididi.booking.invoice.web;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.service.BookingService;
import com.dididi.booking.invoice.service.InvoiceService;
import com.dididi.booking.web.CurrentUser;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

/** Nhan vien tai hoa don VAT cho don cua chinh minh (don tra bang ngan sach cong ty). */
@Controller
public class InvoiceWebController {

    private final InvoiceService invoiceService;
    private final BookingService bookingService;
    private final CurrentUser currentUser;

    public InvoiceWebController(InvoiceService invoiceService, BookingService bookingService, CurrentUser currentUser) {
        this.invoiceService = invoiceService;
        this.bookingService = bookingService;
        this.currentUser = currentUser;
    }

    @GetMapping("/account/bookings/{code}/invoice")
    @ResponseBody
    public ResponseEntity<ByteArrayResource> invoice(@PathVariable String code, Authentication auth) {
        Booking b = bookingService.getForUser(code, currentUser.id(auth)); // dam bao don thuoc user
        byte[] pdf = invoiceService.generateForBooking(b.getPublicCode());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"HoaDon-" + code + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(new ByteArrayResource(pdf));
    }
}
