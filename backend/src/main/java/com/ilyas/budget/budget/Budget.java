package com.ilyas.budget.budget;

import com.ilyas.budget.auth.User;
import com.ilyas.budget.category.Category;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "budgets")
public class Budget {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    private LocalDate month;

    private BigDecimal amountLimit;

    protected Budget() {
    }

    public Budget(User user, Category category, LocalDate month, BigDecimal amountLimit) {
        this.user = user;
        this.category = category;
        this.month = month.withDayOfMonth(1);
        this.amountLimit = amountLimit;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Category getCategory() {
        return category;
    }

    public LocalDate getMonth() {
        return month;
    }

    public BigDecimal getAmountLimit() {
        return amountLimit;
    }
}
