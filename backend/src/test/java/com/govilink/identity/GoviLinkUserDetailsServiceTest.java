package com.govilink.identity;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GoviLinkUserDetailsServiceTest {

    private AppUserRepository repository;
    private GoviLinkUserDetailsService service;

    @BeforeEach
    void setUp() {
        repository = mock(AppUserRepository.class);
        service = new GoviLinkUserDetailsService(repository);
    }

    @Test
    void loadsNormalizedEmailPasswordHashAndAdminAuthority() {
        AppUser account = account(Set.of(UserRole.ADMIN));

        when(repository.findByEmailIgnoreCase("admin@example.com"))
                .thenReturn(Optional.of(account));

        UserDetails details = service.loadUserByUsername(
                "  ADMIN@EXAMPLE.COM  "
        );

        assertEquals("admin@example.com", details.getUsername());
        assertEquals(account.getPasswordHash(), details.getPassword());
        assertEquals(Set.of("ROLE_ADMIN"), authorities(details));
        assertTrue(details.isEnabled());

        verify(repository).findByEmailIgnoreCase("admin@example.com");
    }

    @Test
    void mapsEveryAssignedRoleWithoutAddingOtherPermissions() {
        AppUser account = account(
                Set.of(UserRole.COORDINATOR, UserRole.BUYER)
        );

        when(repository.findByEmailIgnoreCase("admin@example.com"))
                .thenReturn(Optional.of(account));

        UserDetails details = service.loadUserByUsername(
                "admin@example.com"
        );

        assertEquals(
                Set.of("ROLE_COORDINATOR", "ROLE_BUYER"),
                authorities(details)
        );
    }

    @Test
    void marksInactiveAccountsAsDisabled() {
        AppUser account = account(Set.of(UserRole.ADMIN));
        account.deactivate();

        when(repository.findByEmailIgnoreCase("admin@example.com"))
                .thenReturn(Optional.of(account));

        UserDetails details = service.loadUserByUsername(
                "admin@example.com"
        );

        assertFalse(details.isEnabled());
    }

    @Test
    void rejectsAnUnknownEmail() {
        when(repository.findByEmailIgnoreCase("missing@example.com"))
                .thenReturn(Optional.empty());

        assertThrows(
                UsernameNotFoundException.class,
                () -> service.loadUserByUsername("missing@example.com")
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void rejectsMissingEmailWithoutQueryingTheDatabase(String email) {
        assertThrows(
                UsernameNotFoundException.class,
                () -> service.loadUserByUsername(email)
        );

        verifyNoInteractions(repository);
    }

    private AppUser account(Set<UserRole> roles) {
        return new AppUser(
                "admin@example.com",
                "Test Admin",
                "test-only-password-hash",
                roles
        );
    }

    private Set<String> authorities(UserDetails details) {
        return details.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }
}