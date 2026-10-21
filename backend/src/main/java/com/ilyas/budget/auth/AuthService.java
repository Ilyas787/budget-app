package com.ilyas.budget.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse register(RegisterRequest request) {
        String email = User.normaliserEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new EmailDejaUtiliseException(email);
        }

        String hash = passwordEncoder.encode(request.password());
        User user = new User(email, hash, request.displayName());
        userRepository.save(user);
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName());
    }

}
