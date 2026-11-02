package com.govilink.identity;

import com.jayway.jsonpath.JsonPath;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({
        IdentityWebSecurityConfig.class,
        IdentityAuthenticationConfig.class,
        IdentityPasswordConfig.class,
        GoviLinkUserDetailsService.class,
        AuthSecurityTest.ProbeController.class
})
class AuthSecurityTest {

    private static final String EMAIL = "admin@example.com";
    private static final String PASSWORD =
            "Test login passphrase 42!";

    private static String passwordHash;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AppUserRepository repository;

    private AppUser account;

    @BeforeAll
    static void encodePassword() {
        PasswordEncoder encoder =
                new IdentityPasswordConfig().passwordEncoder();

        passwordHash = encoder.encode(PASSWORD);
    }

    @BeforeEach
    void setUp() {
        account = account(UserRole.ADMIN);
        useAccount(account);
    }

    @Test
    void anonymousBusinessRequestRequiresLogin()
            throws Exception {
        mvc.perform(get("/api/v1/security-probe"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void csrfEndpointProvidesHeaderAndToken()
            throws Exception {
        mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName")
                        .value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void loginWithoutCsrfIsRejected() throws Exception {
        mvc.perform(
                        post("/api/v1/auth/login")
                                .param("email", EMAIL)
                                .param("password", PASSWORD)
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void wrongPasswordReturnsGenericUnauthorized()
            throws Exception {
        mvc.perform(
                        post("/api/v1/auth/login")
                                .with(csrf())
                                .param("email", EMAIL)
                                .param("password", "wrong")
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail")
                        .value("Invalid email or password."));
    }

    @Test
    void unknownEmailReturnsSameGenericError()
            throws Exception {
        when(repository.findByEmailIgnoreCase(
                "missing@example.com"
        )).thenReturn(Optional.empty());

        mvc.perform(
                        post("/api/v1/auth/login")
                                .with(csrf())
                                .param("email", "missing@example.com")
                                .param("password", PASSWORD)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail")
                        .value("Invalid email or password."));
    }

    @Test
    void inactiveAccountCannotLogin() throws Exception {
        account.deactivate();

        mvc.perform(
                        post("/api/v1/auth/login")
                                .with(csrf())
                                .param("email", EMAIL)
                                .param("password", PASSWORD)
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRotatesExistingSessionId() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String oldId = session.getId();

        mvc.perform(
                        post("/api/v1/auth/login")
                                .session(session)
                                .with(csrf())
                                .param("email", EMAIL)
                                .param("password", PASSWORD)
                )
                .andExpect(status().isNoContent());

        assertNotEquals(oldId, session.getId());
    }

    @Test
    void sessionRestoresAccountWithoutExposingPassword()
            throws Exception {
        mvc.perform(
                        get("/api/v1/auth/me")
                                .session(login())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.displayName")
                        .value("Test Admin"))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash")
                        .doesNotExist());
    }

    @Test
    void adminCanAccessBusinessApis() throws Exception {
        mvc.perform(
                        get("/api/v1/security-probe")
                                .session(login())
                )
                .andExpect(status().isOk());
    }

    @Test
    void coordinatorCanAccessBusinessApis()
            throws Exception {
        useAccount(account(UserRole.COORDINATOR));

        mvc.perform(
                        get("/api/v1/security-probe")
                                .session(login())
                )
                .andExpect(status().isOk());
    }

    @Test
    void buyerCannotAccessInternalBusinessApis()
            throws Exception {
        useAccount(account(UserRole.BUYER));

        mvc.perform(
                        get("/api/v1/security-probe")
                                .session(login())
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedWritesStillRequireCsrf()
            throws Exception {
        mvc.perform(
                        post("/api/v1/security-probe")
                                .session(login())
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutRequiresCsrf() throws Exception {
        mvc.perform(
                        post("/api/v1/auth/logout")
                                .session(login())
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        MockHttpSession session = login();

        mvc.perform(
                        post("/api/v1/auth/logout")
                                .session(session)
                                .with(csrf())
                )
                .andExpect(status().isNoContent());

        assertTrue(session.isInvalid());

        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deactivationRevokesExistingSession()
            throws Exception {
        MockHttpSession session = login();
        account.deactivate();

        mvc.perform(
                        get("/api/v1/security-probe")
                                .session(session)
                )
                .andExpect(status().isUnauthorized());

        assertTrue(session.isInvalid());
    }

    @Test
    void roleChangeRevokesExistingSession()
            throws Exception {
        MockHttpSession session = login();
        useAccount(account(UserRole.BUYER));

        mvc.perform(
                        get("/api/v1/security-probe")
                                .session(session)
                )
                .andExpect(status().isUnauthorized());

        assertTrue(session.isInvalid());
    }

    @Test
    void realCsrfTokenIsRotatedAfterLogin()
            throws Exception {
        MvcResult initial = mvc.perform(
                        get("/api/v1/auth/csrf")
                )
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session =
                (MockHttpSession) initial.getRequest()
                        .getSession(false);

        String oldToken = JsonPath.read(
                initial.getResponse().getContentAsString(),
                "$.token"
        );

        mvc.perform(
                        post("/api/v1/auth/login")
                                .session(session)
                                .header("X-CSRF-TOKEN", oldToken)
                                .param("email", EMAIL)
                                .param("password", PASSWORD)
                )
                .andExpect(status().isNoContent());

        mvc.perform(
                        post("/api/v1/auth/logout")
                                .session(session)
                                .header("X-CSRF-TOKEN", oldToken)
                )
                .andExpect(status().isForbidden());

        MvcResult fresh = mvc.perform(
                        get("/api/v1/auth/csrf")
                                .session(session)
                )
                .andExpect(status().isOk())
                .andReturn();

        String newToken = JsonPath.read(
                fresh.getResponse().getContentAsString(),
                "$.token"
        );

        mvc.perform(
                        post("/api/v1/auth/logout")
                                .session(session)
                                .header("X-CSRF-TOKEN", newToken)
                )
                .andExpect(status().isNoContent());
    }

    private AppUser account(UserRole role) {
        return new AppUser(
                EMAIL,
                "Test Admin",
                passwordHash,
                Set.of(role)
        );
    }

    private void useAccount(AppUser value) {
        when(repository.findByEmailIgnoreCase(EMAIL))
                .thenReturn(Optional.of(value));
    }

    private MockHttpSession login() throws Exception {
        MvcResult result = mvc.perform(
                        post("/api/v1/auth/login")
                                .with(csrf())
                                .param("email", EMAIL)
                                .param("password", PASSWORD)
                )
                .andExpect(status().isNoContent())
                .andReturn();

        MockHttpSession session =
                (MockHttpSession) result.getRequest()
                        .getSession(false);

        assertNotNull(session);

        return session;
    }

    @RestController
    static class ProbeController {

        @GetMapping("/api/v1/security-probe")
        public String probe() {
            return "OK";
        }
    }
}