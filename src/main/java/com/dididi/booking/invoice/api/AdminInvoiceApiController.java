package com.dididi.booking.invoice.api;

import com.dididi.booking.invoice.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN/SUPER_ADMIN tai hoa don VAT cua bat ky don cong ty nao. */
@Tag(name = "Admin - Hoa don VAT (B2B)")
@RestController
@RequestMapping("/api/admin/v1/invoices")
public class AdminInvoiceApiController {

    private final InvoiceService invoiceService;

    public AdminInvoiceApiController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @Operation(summary = "Tải hóa đơn VAT (PDF) theo mã đơn")
    @GetMapping("/{code}")
    public ResponseEntity<byte[]> invoice(@PathVariable String code) {
        byte[] pdf = invoiceService.generateForBooking(code);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"HoaDon-" + code + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
