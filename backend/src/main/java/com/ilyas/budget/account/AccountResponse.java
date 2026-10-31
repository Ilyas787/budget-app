package com.ilyas.budget.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String name,
        AccountType type,
        String currency,
        BigDecimal initialBalance,
        Instant createdAt
) {
    static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getType(),
                account.getCurrency(),
                account.getInitialBalance(),
                account.getCreatedAt()
        );
    }
}
