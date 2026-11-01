package com.govilink.identity;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository
        extends JpaRepository<AppUser, UUID> {

    @EntityGraph(attributePaths = "roles")
    Optional<AppUser> findByEmailIgnoreCase(String email);

    @Override
    @EntityGraph(attributePaths = "roles")
    Optional<AppUser> findById(UUID id);

    @Query("""
            select count(distinct user.id)
            from AppUser user
            join user.roles role
            where role = :role
            """)
    long countByRole(@Param("role") UserRole role);
}