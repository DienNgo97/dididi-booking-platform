package com.dididi.booking.social.api.dto;

import java.util.List;

/** Mot trang feed/explore theo keyset: nextCursor = null khi het. */
public record SocialFeedPage(
        List<PostView> items,
        Long nextCursor
) {
}
