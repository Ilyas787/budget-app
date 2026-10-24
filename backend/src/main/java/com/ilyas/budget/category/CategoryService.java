package com.ilyas.budget.category;

import com.ilyas.budget.auth.User;
import com.ilyas.budget.transaction.TransactionKind;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryService {
    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public void creerCategoriesParDefaut(User user) {
        List<Category> categories = List.of(
                new Category(user, "Logement", TransactionKind.DEPENSE),
                new Category(user, "Alimentation", TransactionKind.DEPENSE),
                new Category(user, "Transport", TransactionKind.DEPENSE),
                new Category(user, "Loisirs", TransactionKind.DEPENSE),
                new Category(user, "Santé", TransactionKind.DEPENSE),
                new Category(user, "Vêtements", TransactionKind.DEPENSE),
                new Category(user, "Salaire", TransactionKind.REVENU),
                new Category(user, "Autres revenus", TransactionKind.REVENU)
        );
        categoryRepository.saveAll(categories);
    }
}
