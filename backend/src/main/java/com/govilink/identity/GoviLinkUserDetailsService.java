package com.govilink.identity;

import java.util.Locale;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoviLinkUserDetailsService implements UserDetailsService {

    private final AppUserRepository repository;

    public GoviLinkUserDetailsService(AppUserRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        if (email == null || email.isBlank()) {
            throw accountNotFound();
        }

        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);

        AppUser account = repository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(this::accountNotFound);

        String[] roles = account.getRoles().stream()
                .map(UserRole::name)
                .sorted()
                .toArray(String[]::new);

        return User.withUsername(account.getEmail())
                .password(account.getPasswordHash())
                .roles(roles)
                .disabled(!account.isActive())
                .build();
    }

    private UsernameNotFoundException accountNotFound() {
        return new UsernameNotFoundException("Account not found.");
    }
}