package com.ilyas.budget.auth;

import com.ilyas.budget.category.CategoryService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CategoryService categoryService;
    private final JwtEncoder jwtEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, CategoryService categoryService, JwtEncoder jwtEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.categoryService = categoryService;
        this.jwtEncoder = jwtEncoder;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = User.normaliserEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new EmailDejaUtiliseException(email);
        }

        String hash = passwordEncoder.encode(request.password());
        User user = new User(email, hash, request.displayName());
        userRepository.save(user);
        categoryService.creerCategoriesParDefaut(user);
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName());
    }

    public User authentifier(LoginRequest request) {
        User user = userRepository.findByEmail(User.normaliserEmail(request.email()))
                .orElseThrow(() -> new IdentifiantsInvalidesException());
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IdentifiantsInvalidesException();
        }
        return user;
    }

    public TokenResponse login(LoginRequest request) {
        User user = authentifier(request);

        Instant maintenant = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("budget-app")
                .subject(user.getId().toString())
                .issuedAt(maintenant)
                .expiresAt(maintenant.plus(1, ChronoUnit.HOURS))  //
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token);
    }

}
