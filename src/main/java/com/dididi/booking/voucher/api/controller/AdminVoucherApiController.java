package com.dididi.booking.voucher.api.controller;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.voucher.api.dto.VoucherDto;
import com.dididi.booking.voucher.api.dto.VoucherUpsertRequest;
import com.dididi.booking.voucher.service.VoucherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin - Voucher", description = "Quản lý mã giảm giá (ADMIN/SUPER_ADMIN).")
@RestController
@RequestMapping("/api/admin/v1/vouchers")
public class AdminVoucherApiController {

    private final VoucherService voucherService;

    public AdminVoucherApiController(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @Operation(summary = "Danh sách voucher")
    @GetMapping
    public ApiResponse<List<VoucherDto>> list() {
        return ApiResponse.ok(voucherService.list().stream().map(VoucherDto::from).toList());
    }

    @Operation(summary = "Chi tiết voucher")
    @GetMapping("/{id}")
    public ApiResponse<VoucherDto> get(@PathVariable Long id) {
        return ApiResponse.ok(VoucherDto.from(voucherService.get(id)));
    }

    @Operation(summary = "Tạo voucher. discountType=PERCENT|FIXED")
    @PostMapping
    public ApiResponse<VoucherDto> create(@RequestBody VoucherUpsertRequest req) {
        return ApiResponse.ok(VoucherDto.from(voucherService.create(req)), "Đã tạo voucher");
    }

    @Operation(summary = "Cập nhật voucher")
    @PutMapping("/{id}")
    public ApiResponse<VoucherDto> update(@PathVariable Long id, @RequestBody VoucherUpsertRequest req) {
        return ApiResponse.ok(VoucherDto.from(voucherService.update(id, req)), "Đã cập nhật voucher");
    }

    @Operation(summary = "Xoá voucher")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        voucherService.delete(id);
        return ApiResponse.ok(null, "Đã xoá voucher");
    }
}
