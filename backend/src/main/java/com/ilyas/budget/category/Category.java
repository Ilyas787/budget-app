package com.ilyas.budget.category;

import com.ilyas.budget.auth.User;
import com.ilyas.budget.transaction.TransactionKind;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "categories")
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private String name;

    @Enumerated(EnumType.STRING)
    private TransactionKind kind;

    private String color;

    protected Category() {
    }

    public Category(User user, String name, TransactionKind kind, String color) {
        this.user = user;
        this.name = name;
        this.kind = kind;
        this.color = color;
    }

    public Category(User user, String name, TransactionKind kind) {
        this(user, name, kind, null);
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getName() {
        return name;
    }

    public TransactionKind getKind() {
        return kind;
    }

    public String getColor() {
        return color;
    }


}
