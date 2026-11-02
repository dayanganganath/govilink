package com.govilink.identity;

import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AppUserRepository repository;

    public AuthController(AppUserRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(
                token.getHeaderName(),
                token.getToken()
        );
    }

    @GetMapping("/me")
    public AccountResponse me(Authentication authentication) {
        if (authentication == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED
            );
        }

        AppUser account = repository
                .findByEmailIgnoreCase(authentication.getName())
                .filter(AppUser::isActive)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Please sign in again."
                        )
                );

        return new AccountResponse(
                account.getId(),
                account.getEmail(),
                account.getDisplayName(),
                account.getRoles()
        );
    }

    public record CsrfResponse(
            String headerName,
            String token
    ) {
    }

    public record AccountResponse(
            UUID id,
            String email,
            String displayName,
            Set<UserRole> roles
    ) {
    }
}