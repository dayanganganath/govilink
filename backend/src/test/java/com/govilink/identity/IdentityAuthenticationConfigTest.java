package com.govilink.identity;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdentityAuthenticationConfigTest {

    private static final String EMAIL = "admin@example.com";
    private static final String PASSWORD = "Test login passphrase 42!";

    private AppUserRepository repository;
    private PasswordEncoder passwordEncoder;
    private AuthenticationManager authenticationManager;

    @BeforeEach
    void setUp() {
        repository = mock(AppUserRepository.class);

        passwordEncoder =
                new IdentityPasswordConfig().passwordEncoder();

        GoviLinkUserDetailsService userDetailsService =
                new GoviLinkUserDetailsService(repository);

        authenticationManager =
                new IdentityAuthenticationConfig().authenticationManager(
                        userDetailsService,
                        passwordEncoder
                );
    }

    @Test
    void authenticatesCorrectPasswordAndRetainsAdminRole() {
        registerAccount(PASSWORD, true);

        Authentication result = authenticate(
                "  ADMIN@EXAMPLE.COM  ",
                PASSWORD
        );

        assertTrue(result.isAuthenticated());
        assertEquals(EMAIL, result.getName());

        assertTrue(
                result.getAuthorities().stream()
                        .anyMatch(authority ->
                                authority.getAuthority().equals("ROLE_ADMIN")
                        )
        );

        assertFalse(
                result.getAuthorities().stream()
                        .anyMatch(authority ->
                                authority.getAuthority()
                                        .equals("ROLE_COORDINATOR")
                        )
        );

        assertNull(result.getCredentials());

        UserDetails principal = (UserDetails) result.getPrincipal();
        assertNull(principal.getPassword());
    }

    @Test
    void rejectsWrongPassword() {
        registerAccount(PASSWORD, true);

        assertThrows(
                BadCredentialsException.class,
                () -> authenticate(EMAIL, "Incorrect password")
        );
    }

    @Test
    void hidesWhetherAnEmailExists() {
        when(repository.findByEmailIgnoreCase("missing@example.com"))
                .thenReturn(Optional.empty());

        assertThrows(
                BadCredentialsException.class,
                () -> authenticate("missing@example.com", PASSWORD)
        );
    }

    @Test
    void rejectsDisabledAccountEvenWithCorrectPassword() {
        registerAccount(PASSWORD, false);

        assertThrows(
                DisabledException.class,
                () -> authenticate(EMAIL, PASSWORD)
        );
    }

    @Test
    void treatsPasswordSpacesAsPartOfThePassword() {
        String passwordWithSpaces = "  " + PASSWORD + "  ";

        registerAccount(passwordWithSpaces, true);

        assertThrows(
                BadCredentialsException.class,
                () -> authenticate(EMAIL, PASSWORD)
        );

        Authentication result = authenticate(
                EMAIL,
                passwordWithSpaces
        );

        assertTrue(result.isAuthenticated());
    }

    private void registerAccount(String rawPassword, boolean active) {
        AppUser account = new AppUser(
                EMAIL,
                "Test Admin",
                passwordEncoder.encode(rawPassword),
                Set.of(UserRole.ADMIN)
        );

        if (!active) {
            account.deactivate();
        }

        when(repository.findByEmailIgnoreCase(EMAIL))
                .thenReturn(Optional.of(account));
    }

    private Authentication authenticate(String email, String password) {
        return authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        email,
                        password
                )
        );
    }
}