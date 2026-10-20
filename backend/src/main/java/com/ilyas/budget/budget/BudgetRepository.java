package com.ilyas.budget.budget;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    Optional<Budget> findByUserIdAndCategoryIdAndMonth(UUID userId, UUID categoryId, LocalDate month);

    List<Budget> findAllByUserIdAndMonth(UUID userId, LocalDate month);
}
