package com.smartchat.smartchat.api;

import com.smartchat.smartchat.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiTests extends IntegrationTestBase {

    @Test
    void registerReturnsTokenAndNormalizesEmail() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "MixedCase1", "email": "Mixed.Case1@Example.COM", "password": "secret123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value("mixed.case1@example.com"))
                .andExpect(jsonPath("$.tier").value("FREE"));
    }

    @Test
    void newUsersAreAddedToGeneral() throws Exception {
        TestUser user = registerUser();

        mockMvc.perform(get("/api/rooms/mine").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("general")));
    }

    @Test
    void duplicateEmailIsAConflict() throws Exception {
        TestUser existing = registerUser();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "someone_else", "email": "%s", "password": "secret123"}
                                """.formatted(existing.email())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("Email is already registered."));
    }

    @Test
    void invalidRegistrationListsEveryBadField() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "ab", "email": "not-an-email", "password": "123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void loginWithWrongPasswordIsUnauthorized() throws Exception {
        TestUser user = registerUser();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "wrong-password"}
                                """.formatted(user.email())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password."));
    }

    @Test
    void loginWithCorrectPasswordReturnsToken() throws Exception {
        TestUser user = registerUser();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(user.email(), PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.username").value(user.username()));
    }

    @Test
    void protectedEndpointWithoutTokenIs401() throws Exception {
        mockMvc.perform(get("/api/rooms/mine"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithGarbageTokenIs401() throws Exception {
        mockMvc.perform(get("/api/rooms/mine").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }
}
