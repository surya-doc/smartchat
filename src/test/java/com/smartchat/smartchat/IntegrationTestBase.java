package com.smartchat.smartchat;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared setup for API tests. Every subclass uses the same configuration, so Spring reuses
 * one application context (and one MySQL container) for the whole test run.
 * Each test registers its own users with random names, so tests don't depend on each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTestBase {

    protected static final String PASSWORD = "secret123";

    @Autowired
    protected MockMvc mockMvc;

    protected TestUser registerUser() throws Exception {
        String username = "user_" + UUID.randomUUID().toString().substring(0, 8);
        String email = username + "@example.com";
        String body = """
                {"username": "%s", "email": "%s", "password": "%s"}
                """.formatted(username, email, PASSWORD);

        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return new TestUser(username, email, JsonPath.read(json, "$.token"));
    }

    protected long createRoom(TestUser owner, String name, boolean isPrivate) throws Exception {
        String body = """
                {"name": "%s", "description": "Test room", "isPrivate": %s}
                """.formatted(name, isPrivate);

        String json = mockMvc.perform(post("/api/rooms")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    protected long firstPendingRequestId(TestUser admin, long roomId) throws Exception {
        String json = mockMvc.perform(get("/api/rooms/{id}/join-requests", roomId)
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return ((Number) JsonPath.read(json, "$[0].id")).longValue();
    }

    protected record TestUser(String username, String email, String token) {
        public String bearer() {
            return "Bearer " + token;
        }
    }
}
