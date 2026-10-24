package com.foodieexpress.userservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodieexpress.userservice.dto.LoginRequest;
import com.foodieexpress.userservice.dto.RegisterRequest;
import com.foodieexpress.userservice.exception.EmailAlreadyRegisteredException;
import com.foodieexpress.userservice.exception.InvalidCredentialsException;
import com.foodieexpress.userservice.model.User;
import com.foodieexpress.userservice.model.UserRole;
import com.foodieexpress.userservice.model.UserStatus;
import com.foodieexpress.userservice.repository.UserRepository;
import com.foodieexpress.userservice.security.JwtService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

class UserServiceTests {

    private UserRepository userRepository;
    private JwtService jwtService;
    private UserService userService;
    private Pbkdf2PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository = org.mockito.Mockito.mock(UserRepository.class);
        jwtService = org.mockito.Mockito.mock(JwtService.class);
        passwordEncoder = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        userService = new UserService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void registrationNormalizesEmailHashesPasswordAndAssignsCustomerRole() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User registered = userService.register(
                new RegisterRequest("  Person@Example.com ", "long-test-password", " Person "));

        assertEquals("person@example.com", registered.getEmail());
        assertNotEquals("long-test-password", registered.getPasswordHash());
        assertEquals(true, passwordEncoder.matches("long-test-password", registered.getPasswordHash()));
        assertEquals(java.util.Set.of(UserRole.CUSTOMER), registered.getRoles());
        assertEquals(UserStatus.ACTIVE, registered.getStatus());
        verify(userRepository).save(registered);
    }

    @Test
    void duplicateIndexFailureBecomesSafeConflict() {
        when(userRepository.save(any(User.class))).thenThrow(new DuplicateKeyException("duplicate"));

        assertThrows(EmailAlreadyRegisteredException.class, () -> userService.register(
                new RegisterRequest("person@example.com", "long-test-password", "Person")));
    }

    @Test
    void loginUsesStoredHashAndOnlyIssuesTokenForActiveUser() {
        User user = new User(
                "person@example.com",
                passwordEncoder.encode("long-test-password"),
                "Person",
                Instant.now());
        user.setId("671000000000000000000001");
        when(userRepository.findByEmail("person@example.com")).thenReturn(Optional.of(user));
        when(jwtService.issue(user)).thenReturn(new JwtService.IssuedToken("signed-token", Instant.now()));

        UserService.LoginResult result = userService.login(
                new LoginRequest(" Person@Example.com ", "long-test-password"));

        assertEquals("signed-token", result.accessToken());
        verify(jwtService).issue(user);
    }

    @Test
    void loginFailureIsGenericForUnknownAndDisabledAccounts() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> userService.login(
                new LoginRequest("unknown@example.com", "long-test-password")));
        verify(jwtService, never()).issue(any(User.class));

        User disabled = new User(
                "disabled@example.com",
                passwordEncoder.encode("long-test-password"),
                "Disabled",
                Instant.now());
        disabled.setStatus(UserStatus.DISABLED);
        when(userRepository.findByEmail("disabled@example.com")).thenReturn(Optional.of(disabled));
        assertThrows(InvalidCredentialsException.class, () -> userService.login(
                new LoginRequest("disabled@example.com", "long-test-password")));
        verify(jwtService, never()).issue(any(User.class));
    }
}
