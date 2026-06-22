package com.dididi.booking.booking.domain.enums;

/**
 * Trang thai yeu cau huy don do khach hang gui.
 *  NONE      - chua co yeu cau huy
 *  REQUESTED - khach da gui yeu cau huy (kem ly do), cho admin duyet
 *  APPROVED  - admin da duyet huy (don chuyen CANCELLED + hoan tien thuc tra)
 *  REJECTED  - admin tu choi huy (don giu nguyen)
 */
public enum CancelStatus { NONE, REQUESTED, APPROVED, REJECTED }
