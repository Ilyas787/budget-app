package com.ilyas.budget.account;

import com.ilyas.budget.auth.User;
import com.ilyas.budget.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.Optional;

import static com.ilyas.budget.account.AccountType.COURANT;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class AccountRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    UserRepository userRepository;

    @Autowired
    AccountRepository accountRepository;

    @Test
    void un_user_ne_voit_pas_le_compte_d_un_autre() {
        // given
        User userA = new User("userA@test.com", "password", "userA");
        User userB = new User("userB@test.com", "password", "userB");
        Account accountA = new Account(userA, "accountA", COURANT, BigDecimal.ZERO);
        userRepository.save(userA);
        userRepository.save(userB);
        accountRepository.save(accountA);
        // when
        Optional<Account> accountAvuParA = accountRepository.findByIdAndUserId(accountA.getId(), userA.getId());
        Optional<Account> accountAvuParB = accountRepository.findByIdAndUserId(accountA.getId(), userB.getId());
        // then
        assertThat(accountAvuParA).isPresent();
        assertThat(accountAvuParB).isEmpty();
    }
}
