package com.dididi.booking.loyalty.api.dto;

import java.util.List;

public record LoyaltyAccountDto(Long userId, int balance, String tier, int lifetimeEarned, List<LoyaltyTxnDto> history) {}
