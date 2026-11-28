package com.smartchat.smartchat.api;

import com.smartchat.smartchat.IntegrationTestBase;
import com.smartchat.smartchat.service.PresenceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserApiTests extends IntegrationTestBase {

    @Autowired
    private PresenceService presenceService;

    @Test
    void meReturnsTheCurrentUser() throws Exception {
        TestUser user = registerUser();

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(user.username()))
                .andExpect(jsonPath("$.email").value(user.email()))
                .andExpect(jsonPath("$.tier").value("FREE"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void onlineListFollowsWebSocketSessions() throws Exception {
        TestUser user = registerUser();
        String sessionId = UUID.randomUUID().toString();

        presenceService.sessionOpened(sessionId, user.email());
        mockMvc.perform(get("/api/users/online").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItem(user.username())));

        presenceService.sessionClosed(sessionId);
        mockMvc.perform(get("/api/users/online").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(jsonPath("$", not(hasItem(user.username()))));
    }
}
