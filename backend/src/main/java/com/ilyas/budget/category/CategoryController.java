package com.ilyas.budget.category;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse creer(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CategoryCreateRequest request) {
        return categoryService.creer(userId(jwt), request);
    }

    @GetMapping
    public List<CategoryResponse> lister(@AuthenticationPrincipal Jwt jwt) {
        return categoryService.lister(userId(jwt));
    }

    @GetMapping("/{id}")
    public CategoryResponse lire(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return categoryService.lire(userId(jwt), id);
    }

    @PutMapping("/{id}")
    public CategoryResponse modifier(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                     @Valid @RequestBody CategoryUpdateRequest request) {
        return categoryService.modifier(userId(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        categoryService.supprimer(userId(jwt), id);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
