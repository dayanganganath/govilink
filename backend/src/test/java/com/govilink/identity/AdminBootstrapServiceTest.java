package com.govilink.identity;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminBootstrapServiceTest {

    private static final String PASSWORD =
            "A strong test passphrase 42!";

    private AppUserRepository repository;
    private JdbcTemplate jdbc;
    private PasswordEncoder encoder;
    private LocalValidatorFactoryBean validator;
    private AdminBootstrapService service;

    @BeforeEach
    void setUp() {
        repository = mock(AppUserRepository.class);
        jdbc = mock(JdbcTemplate.class);

        encoder = new IdentityPasswordConfig()
                .passwordEncoder();

        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        service = new AdminBootstrapService(
                repository,
                encoder,
                validator,
                jdbc
        );
    }

    @AfterEach
    void closeValidator() {
        if (validator != null) {
            validator.close();
        }
    }

    @Test
    void storesEncodedPasswordAndAdminRoleWithNormalizedDetails() {
        when(repository.saveAndFlush(any(AppUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.createFirstAdmin(
                "  Admin@EXAMPLE.TEST  ",
                "  System Admin  ",
                PASSWORD
        );

        var captor = ArgumentCaptor.forClass(AppUser.class);

        verify(repository).saveAndFlush(captor.capture());

        AppUser saved = captor.getValue();

        assertEquals("admin@example.test", result.email());
        assertEquals("System Admin", saved.getDisplayName());
        assertEquals(Set.of(UserRole.ADMIN), saved.getRoles());

        assertNotEquals(PASSWORD, saved.getPasswordHash());

        assertTrue(
                saved.getPasswordHash().startsWith("{bcrypt}")
        );

        assertTrue(
                encoder.matches(PASSWORD, saved.getPasswordHash())
        );

        assertFalse(
                encoder.matches(
                        "An incorrect password 42!",
                        saved.getPasswordHash()
                )
        );

        verify(jdbc).execute(
                "select pg_advisory_xact_lock(70641001)"
        );
    }

    @Test
    void saltsEachPasswordEncodingIndependently() {
        String first = encoder.encode(PASSWORD);
        String second = encoder.encode(PASSWORD);

        assertNotEquals(first, second);
        assertTrue(encoder.matches(PASSWORD, first));
        assertTrue(encoder.matches(PASSWORD, second));
    }

    @Test
    void refusesAnotherAdminWithoutSaving() {
        when(repository.countByRole(UserRole.ADMIN))
                .thenReturn(1L);

        assertThrows(
                IllegalStateException.class,
                () -> service.createFirstAdmin(
                        "admin@example.test",
                        "Admin",
                        PASSWORD
                )
        );

        verify(repository, never())
                .saveAndFlush(any(AppUser.class));
    }

    @Test
    void refusesAnEmailAlreadyOwnedByAnotherAccount() {
        when(repository.findByEmailIgnoreCase(
                "admin@example.test"
        )).thenReturn(Optional.of(mock(AppUser.class)));

        assertThrows(
                IllegalStateException.class,
                () -> service.createFirstAdmin(
                        "admin@example.test",
                        "Admin",
                        PASSWORD
                )
        );

        verify(repository, never())
                .saveAndFlush(any(AppUser.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-email", ""})
    void rejectsInvalidEmailBeforeDatabaseCalls(String email) {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.createFirstAdmin(
                        email,
                        "Admin",
                        PASSWORD
                )
        );

        verifyNoInteractions(repository, jdbc);
    }

    @Test
    void rejectsShortPasswordBeforeDatabaseCalls() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.createFirstAdmin(
                        "admin@example.test",
                        "Admin",
                        "short"
                )
        );

        verifyNoInteractions(repository, jdbc);
    }

    @Test
    void rejectsPasswordExceedingBcryptByteLimit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.createFirstAdmin(
                        "admin@example.test",
                        "Admin",
                        "ඔ".repeat(25)
                )
        );

        verifyNoInteractions(repository, jdbc);
    }

    @Test
    void rejectsBlankDisplayNameBeforeDatabaseCalls() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.createFirstAdmin(
                        "admin@example.test",
                        "   ",
                        PASSWORD
                )
        );

        verifyNoInteractions(repository, jdbc);
    }
}