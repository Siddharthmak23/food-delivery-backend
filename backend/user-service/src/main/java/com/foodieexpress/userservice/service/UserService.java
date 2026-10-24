package com.foodieexpress.userservice.service;

import com.foodieexpress.userservice.dto.LoginRequest;
import com.foodieexpress.userservice.dto.RegisterRequest;
import com.foodieexpress.userservice.dto.UpdateProfileRequest;
import com.foodieexpress.userservice.exception.EmailAlreadyRegisteredException;
import com.foodieexpress.userservice.exception.InvalidCredentialsException;
import com.foodieexpress.userservice.exception.ResourceNotFoundException;
import com.foodieexpress.userservice.model.User;
import com.foodieexpress.userservice.model.UserStatus;
import com.foodieexpress.userservice.repository.UserRepository;
import com.foodieexpress.userservice.security.JwtService;
import java.time.Instant;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public User register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        Instant now = Instant.now();
        User user = new User(
                normalizedEmail,
                passwordEncoder.encode(request.password()),
                request.displayName().trim(),
                now);
        try {
            return userRepository.save(user);
        } catch (DuplicateKeyException exception) {
            throw new EmailAlreadyRegisteredException();
        }
    }

    public LoginResult login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        User user = userRepository.findByEmail(normalizedEmail)
                .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        JwtService.IssuedToken token = jwtService.issue(user);
        return new LoginResult(user, token.value(), token.expiresAt());
    }

    public User getActiveUser(String userId) {
        return userRepository.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(ResourceNotFoundException::new);
    }

    public User updateProfile(String userId, UpdateProfileRequest request) {
        User user = getActiveUser(userId);
        user.setDisplayName(request.displayName().trim());
        user.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
        user.setUpdatedAt(Instant.now());
        return userRepository.save(user);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public record LoginResult(User user, String accessToken, Instant expiresAt) {
    }
}
