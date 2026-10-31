package com.ilyas.budget.account;

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
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse creer(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AccountRequest request) {
        return accountService.creer(userId(jwt), request);
    }

    @GetMapping
    public List<AccountResponse> lister(@AuthenticationPrincipal Jwt jwt) {
        return accountService.lister(userId(jwt));
    }

    @GetMapping("/{id}")
    public AccountResponse lire(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return accountService.lire(userId(jwt), id);
    }

    @PutMapping("/{id}")
    public AccountResponse modifier(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                    @Valid @RequestBody AccountRequest request) {
        return accountService.modifier(userId(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        accountService.supprimer(userId(jwt), id);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
