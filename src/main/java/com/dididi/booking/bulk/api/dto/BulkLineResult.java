package com.dididi.booking.bulk.api.dto;

import java.io.Serializable;

/**
 * Ket qua tao 1 dong trong dat theo nhom. status: CONFIRMED / PENDING_APPROVAL / PENDING_PAYMENT / FAILED.
 * implements Serializable: ket qua duoc luu tam vao session (Spring Session/Redis -> JDK serialize).
 */
public record BulkLineResult(int no, String guestName, String code, String status, String message)
        implements Serializable {
    private static final long serialVersionUID = 1L;
}
