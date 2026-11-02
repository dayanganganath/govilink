package com.govilink.identity;

import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminBootstrapService {

    private final AppUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;
    private final JdbcTemplate jdbcTemplate;

    public AdminBootstrapService(
            AppUserRepository repository,
            PasswordEncoder passwordEncoder,
            Validator validator,
            JdbcTemplate jdbcTemplate
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Result createFirstAdmin(
            String email,
            String displayName,
            String rawPassword
    ) {
        String normalizedEmail = email == null
                ? ""
                : email.strip().toLowerCase(Locale.ROOT);

        String normalizedName = displayName == null
                ? ""
                : displayName.strip();

        var details = new Details(
                normalizedEmail,
                normalizedName
        );

        if (!validator.validate(details).isEmpty()) {
            throw new IllegalArgumentException(
                    "Provide a valid admin email and a nonblank "
                            + "display name up to 150 characters."
            );
        }

        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException(
                    "Admin password is required."
            );
        }

        int characters = rawPassword.codePointCount(
                0,
                rawPassword.length()
        );

        int bytes = rawPassword.getBytes(
                StandardCharsets.UTF_8
        ).length;

        if (characters < 15 || characters > 64 || bytes > 72) {
            throw new IllegalArgumentException(
                    "Use a password of 15 to 64 characters "
                            + "and at most 72 UTF-8 bytes."
            );
        }

        jdbcTemplate.execute(
                "select pg_advisory_xact_lock(70641001)"
        );

        if (repository.countByRole(UserRole.ADMIN) > 0) {
            throw new IllegalStateException(
                    "An admin already exists. Bootstrap does not "
                            + "add admins or reset passwords."
            );
        }

        if (repository.findByEmailIgnoreCase(
                normalizedEmail
        ).isPresent()) {
            throw new IllegalStateException(
                    "That email already belongs to an account."
            );
        }

        AppUser user = repository.saveAndFlush(
                new AppUser(
                        normalizedEmail,
                        normalizedName,
                        passwordEncoder.encode(rawPassword),
                        Set.of(UserRole.ADMIN)
                )
        );

        return new Result(
                user.getId(),
                user.getEmail(),
                user.getDisplayName()
        );
    }

    private record Details(
            @NotBlank
            @Email
            @Size(max = 254)
            String email,

            @NotBlank
            @Size(max = 150)
            String displayName
    ) {
    }

    public record Result(
            UUID id,
            String email,
            String displayName
    ) {
    }
}