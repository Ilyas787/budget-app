package com.ilyas.budget.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryUpdateRequest(
        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 50, message = "Le nom ne doit pas faire plus de 50 caractères")
        String name,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "La couleur doit être au format #RRGGBB")
        String color
) {
}
