package com.ilyas.budget.transaction;

import com.ilyas.budget.account.Account;
import com.ilyas.budget.auth.User;
import com.ilyas.budget.category.Category;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Enumerated(EnumType.STRING)
    private TransactionKind kind;

    private BigDecimal amount;

    private LocalDate date;

    private String label;

    private String note;

    private Instant createdAt = Instant.now();

    protected Transaction() {
    }

    public Transaction(User user,
                       Account account,
                       Category category,
                       TransactionKind kind,
                       BigDecimal amount,
                       LocalDate date,
                       String label,
                       String note) {
        this.user = user;
        this.account = account;
        this.category = category;
        this.kind = kind;
        this.amount = amount;
        this.date = date;
        this.label = label;
        this.note = note;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Account getAccount() {
        return account;
    }

    public Category getCategory() {
        return category;
    }

    public TransactionKind getKind() {
        return kind;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getLabel() {
        return label;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
