package com.ilyas.budget.auth;

import com.ilyas.budget.category.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CategoryService categoryService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, categoryService);
    }

    @Test
    void connexion_reussie_avec_le_bon_mot_de_passe() {
        // given
        User user = new User("ilyas@test.com", passwordEncoder.encode("unmotdepasselong"), "Ilyas");
        when(userRepository.findByEmail("ilyas@test.com")).thenReturn(Optional.of(user));

        // when
        User resultat = authService.authentifier(new LoginRequest("Ilyas@Test.com", "unmotdepasselong"));

        // then
        assertThat(resultat).isSameAs(user);
    }

    @Test
    void connexion_refusee_avec_un_email_inconnu() {
        // given
        when(userRepository.findByEmail("ilyas@test.com")).thenReturn(Optional.empty());

        // when + then
        assertThatThrownBy(() -> authService.authentifier(new LoginRequest("Ilyas@Test.com", "unmotdepasselong")))
                .isInstanceOf(IdentifiantsInvalidesException.class);
    }

    @Test
    void connexion_refusee_avec_un_mauvais_password() {
        // given
        User user = new User("ilyas@test.com", passwordEncoder.encode("unmotdepasselong"), "Ilyas");
        when(userRepository.findByEmail("ilyas@test.com")).thenReturn(Optional.of(user));

        // when + then
        assertThatThrownBy(() -> authService.authentifier(new LoginRequest("Ilyas@Test.com", "unmotdepasselon")))
                .isInstanceOf(IdentifiantsInvalidesException.class);
    }
}
