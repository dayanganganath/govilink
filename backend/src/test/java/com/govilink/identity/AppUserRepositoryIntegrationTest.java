package com.govilink.identity;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class AppUserRepositoryIntegrationTest {

    // Schema-test data only. This is not a usable login password/hash.
    private static final String TEST_HASH =
            "test-only-not-a-login-password";

    @Autowired
    private AppUserRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String token =
            UUID.randomUUID().toString().substring(0, 8);

    @Test
    void persistsNormalizedEmailDisplayNameAndRoles() {
        AppUser user = repository.saveAndFlush(
                new AppUser(
                        "  Staff-" + token + "@EXAMPLE.TEST  ",
                        "  Demo Coordinator  ",
                        TEST_HASH,
                        Set.of(UserRole.ADMIN, UserRole.COORDINATOR)
                )
        );

        UUID id = user.getId();
        entityManager.clear();

        AppUser saved = repository.findById(id).orElseThrow();

        assertAll(
                () -> assertEquals(
                        "staff-" + token + "@example.test",
                        saved.getEmail()
                ),
                () -> assertEquals(
                        "Demo Coordinator",
                        saved.getDisplayName()
                ),
                () -> assertEquals(
                        Set.of(UserRole.ADMIN, UserRole.COORDINATOR),
                        saved.getRoles()
                ),
                () -> assertTrue(saved.isActive()),
                () -> assertNotNull(saved.getCreatedAt()),
                () -> assertNotNull(saved.getUpdatedAt())
        );
    }

    @Test
    void findsUserByEmailIgnoringCase() {
        AppUser user = createUser(
                "lookup",
                Set.of(UserRole.COORDINATOR)
        );

        entityManager.clear();

        AppUser found = repository.findByEmailIgnoreCase(
                user.getEmail().toUpperCase(java.util.Locale.ROOT)
        ).orElseThrow();

        assertEquals(user.getId(), found.getId());
        assertEquals(
                Set.of(UserRole.COORDINATOR),
                found.getRoles()
        );
    }

    @Test
    void rejectsDuplicateNormalizedEmail() {
        AppUser first = createUser(
                "duplicate",
                Set.of(UserRole.BUYER)
        );

        AppUser duplicate = new AppUser(
                first.getEmail().toUpperCase(java.util.Locale.ROOT),
                "Another User",
                TEST_HASH,
                Set.of(UserRole.FARMER)
        );

        var exception = assertThrows(
                DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(duplicate)
        );

        assertTrue(
                exception.getMostSpecificCause()
                        .getMessage()
                        .contains("uq_app_users_email")
        );
    }

    @Test
    void persistsDisabledAccountStateAndIncrementsVersion() {
        AppUser user = createUser(
                "disabled",
                Set.of(UserRole.COORDINATOR)
        );

        UUID id = user.getId();
        long originalVersion = user.getVersion();

        user.deactivate();
        repository.saveAndFlush(user);
        entityManager.clear();

        AppUser saved = repository.findById(id).orElseThrow();

        assertFalse(saved.isActive());
        assertEquals(originalVersion + 1, saved.getVersion());
    }

    @Test
    void countsUsersWithRoleWithoutDoubleCountingMultipleRoles() {
        long before = repository.countByRole(UserRole.ADMIN);

        createUser(
                "admin",
                Set.of(UserRole.ADMIN, UserRole.COORDINATOR)
        );

        createUser(
                "buyer",
                Set.of(UserRole.BUYER)
        );

        assertEquals(
                before + 1,
                repository.countByRole(UserRole.ADMIN)
        );
    }

    @Test
    void removingUserAlsoRemovesItsRoleRows() {
        AppUser user = createUser(
                "delete",
                Set.of(UserRole.ADMIN, UserRole.COORDINATOR)
        );

        UUID id = user.getId();

        Long before = jdbcTemplate.queryForObject(
                "select count(*) from app_user_roles where user_id = ?",
                Long.class,
                id
        );

        assertEquals(2L, before.longValue());

        repository.delete(user);
        repository.flush();
        entityManager.clear();

        Long after = jdbcTemplate.queryForObject(
                "select count(*) from app_user_roles where user_id = ?",
                Long.class,
                id
        );

        assertEquals(0L, after.longValue());
        assertTrue(repository.findById(id).isEmpty());
    }

    private AppUser createUser(
            String suffix,
            Set<UserRole> roles
    ) {
        return repository.saveAndFlush(
                new AppUser(
                        "test-" + token + "-" + suffix + "@example.test",
                        "Demo User",
                        TEST_HASH,
                        roles
                )
        );
    }
}