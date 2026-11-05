package com.ilyas.budget.account;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AccountRequest(
        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 50, message = "Le nom ne doit pas faire plus de 50 caractères")
        String name,

        @NotNull(message = "Le type est obligatoire")
        AccountType type,

        @Digits(integer = 10, fraction = 2, message = "Le solde initial doit avoir au plus 10 chiffres avant la virgule et 2 après")
        BigDecimal initialBalance
) {
}
