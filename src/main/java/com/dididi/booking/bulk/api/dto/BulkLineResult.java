package com.dididi.booking.bulk.api.dto;

/** Ket qua tao 1 dong trong dat theo nhom. status: CONFIRMED / PENDING_APPROVAL / PENDING_PAYMENT / FAILED. */
public record BulkLineResult(int no, String guestName, String code, String status, String message) {}
