package com.dididi.booking.vendor.api.dto;

import java.util.List;

/**
 * Ma trận sở thích: mỗi nhóm khách (row) chọn các loại phòng nào (counts theo cột roomTypes).
 * roomTypes = tiêu đề cột; mỗi GroupRow.counts khớp thứ tự với roomTypes.
 */
public record GroupPreferenceDto(List<String> roomTypes, List<GroupRow> rows) {

    public record GroupRow(String group, long total, List<Long> counts) {
    }
}
