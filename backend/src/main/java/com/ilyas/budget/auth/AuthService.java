package com.ilyas.budget.auth;

import com.ilyas.budget.category.CategoryService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CategoryService categoryService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, CategoryService categoryService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.categoryService = categoryService;
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

}
