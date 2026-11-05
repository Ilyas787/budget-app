package com.ilyas.budget.category;

import com.ilyas.budget.transaction.TransactionKind;

import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        TransactionKind kind,
        String color
) {
    static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getKind(),
                category.getColor()
        );
    }
}
