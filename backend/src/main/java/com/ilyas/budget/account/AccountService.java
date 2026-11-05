package com.ilyas.budget.account;

import com.ilyas.budget.auth.UserRepository;
import com.ilyas.budget.common.RessourceIntrouvableException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public AccountService(AccountRepository accountRepository, UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    public AccountResponse creer(UUID userId, AccountRequest request) {
        Account account = new Account(userRepository.getReferenceById(userId), request.name(), request.type(), request.initialBalance());
        accountRepository.save(account);
        return AccountResponse.from(account);
    }

    public List<AccountResponse> lister(UUID userId) {
        return accountRepository.findAllByUserId(userId)
                .stream()
                .map(account -> AccountResponse.from(account))
                .toList();
    }

    public AccountResponse lire(UUID userId, UUID id) {
        return AccountResponse.from(trouverCompte(userId, id));
    }

    public AccountResponse modifier(UUID userId, UUID id, AccountRequest request) {
        Account account = trouverCompte(userId, id);
        account.modifier(request.name(), request.type(), request.initialBalance());
        accountRepository.save(account);
        return AccountResponse.from(account);
    }

    public void supprimer(UUID userId, UUID id) {
        accountRepository.delete(trouverCompte(userId, id));
    }

    private Account trouverCompte(UUID userId, UUID id) {
        return accountRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RessourceIntrouvableException());
    }
}
