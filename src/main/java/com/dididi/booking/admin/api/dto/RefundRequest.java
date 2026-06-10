package com.dididi.booking.admin.api.dto;

/** Body cho POST /api/admin/v1/bookings/{code}/refund. reason co the de trong. */
public record RefundRequest(String reason) {}
