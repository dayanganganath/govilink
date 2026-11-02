package com.govilink.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableWebSecurity
@ConditionalOnWebApplication(
        type = ConditionalOnWebApplication.Type.SERVLET
)
public class IdentityWebSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationManager authenticationManager,
            AppUserRepository repository
    ) throws Exception {
        http.authenticationManager(authenticationManager);

        http.requestCache(cache -> cache.disable());
        http.httpBasic(basic -> basic.disable());

        // Keep Spring Security's session-based CSRF protection enabled.
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                        HttpMethod.GET,
                        "/actuator/health",
                        "/actuator/health/**",
                        "/api/v1/auth/csrf"
                ).permitAll()

                .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/auth/login",
                        "/api/v1/auth/logout"
                ).permitAll()

                .requestMatchers("/api/v1/auth/me")
                        .authenticated()

                .requestMatchers("/api/v1/**")
                        .hasAnyRole("ADMIN", "COORDINATOR")

                .anyRequest().denyAll()
        );

        http.exceptionHandling(errors -> errors
                .authenticationEntryPoint(
                        (request, response, exception) ->
                                problem(
                                        response,
                                        401,
                                        "Authentication required."
                                )
                )
                .accessDeniedHandler(
                        (request, response, exception) ->
                                problem(
                                        response,
                                        403,
                                        "Request not permitted."
                                )
                )
        );

        http.formLogin(form -> form
                .loginPage("/api/v1/auth/login")
                .loginProcessingUrl("/api/v1/auth/login")
                .usernameParameter("email")
                .passwordParameter("password")

                .successHandler(
                        (request, response, authentication) ->
                                response.setStatus(204)
                )

                .failureHandler(
                        (request, response, exception) ->
                                problem(
                                        response,
                                        401,
                                        "Invalid email or password."
                                )
                )
        );

        http.logout(logout -> logout
                .logoutUrl("/api/v1/auth/logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler(
                        (request, response, authentication) ->
                                response.setStatus(204)
                )
        );

        http.sessionManagement(session -> session
                .sessionFixation(fixation ->
                        fixation.changeSessionId()
                )
        );

        http.addFilterBefore(
                new AccountAccessFilter(repository),
                AuthorizationFilter.class
        );

        return http.build();
    }

    private static void problem(
            HttpServletResponse response,
            int status,
            String detail
    ) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");

        response.getWriter().write(
                "{\"status\":" + status
                        + ",\"detail\":\"" + detail + "\"}"
        );
    }

    private static class AccountAccessFilter
            extends OncePerRequestFilter {

        private final AppUserRepository repository;

        AccountAccessFilter(AppUserRepository repository) {
            this.repository = repository;
        }

        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain chain
        ) throws ServletException, IOException {
            Authentication authentication =
                    SecurityContextHolder.getContext()
                            .getAuthentication();

            if (authentication != null
                    && authentication.isAuthenticated()
                    && !(authentication instanceof
                    org.springframework.security.authentication.AnonymousAuthenticationToken)) {

                AppUser account = repository
                        .findByEmailIgnoreCase(
                                authentication.getName()
                        )
                        .orElse(null);

                Set<String> grantedRoles =
                        authentication.getAuthorities().stream()
                                .map(authority ->
                                        authority.getAuthority()
                                )
                                .filter(authority ->
                                        authority.startsWith("ROLE_")
                                )
                                .collect(Collectors.toSet());

                Set<String> currentRoles = account == null
                        ? Set.of()
                        : account.getRoles().stream()
                                .map(role -> "ROLE_" + role.name())
                                .collect(Collectors.toSet());

                if (account == null
                        || !account.isActive()
                        || !grantedRoles.equals(currentRoles)) {

                    SecurityContextHolder.clearContext();

                    var session = request.getSession(false);

                    if (session != null) {
                        session.invalidate();
                    }

                    problem(
                            response,
                            401,
                            "Please sign in again."
                    );

                    return;
                }
            }

            chain.doFilter(request, response);
        }
    }
}