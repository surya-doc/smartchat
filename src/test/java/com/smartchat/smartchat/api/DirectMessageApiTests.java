package com.smartchat.smartchat.api;

import com.smartchat.smartchat.IntegrationTestBase;
import com.smartchat.smartchat.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DirectMessageApiTests extends IntegrationTestBase {

    @Autowired
    private UserRepository userRepository;

    @Test
    void conversationFlow() throws Exception {
        TestUser alice = registerUser();
        TestUser bob = registerUser();
        long aliceId = userRepository.findByEmail(alice.email()).orElseThrow().getId();
        long bobId = userRepository.findByEmail(bob.email()).orElseThrow().getId();

        mockMvc.perform(post("/api/dm")
                        .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverId\": %d, \"content\": \"hi bob\"}".formatted(bobId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderId").value((int) aliceId))
                .andExpect(jsonPath("$.receiverUsername").value(bob.username()))
                .andExpect(jsonPath("$.isRead").value(false));

        mockMvc.perform(post("/api/dm/{id}/read", aliceId).header(HttpHeaders.AUTHORIZATION, bob.bearer()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/dm/{id}", aliceId).header(HttpHeaders.AUTHORIZATION, bob.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].isRead").value(true));

        // bob got a notification for the DM
        mockMvc.perform(get("/api/notifications/unread").header(HttpHeaders.AUTHORIZATION, bob.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("DIRECT_MESSAGE"))
                .andExpect(jsonPath("$[0].referenceId").value((int) aliceId));
    }

    @Test
    void cannotMessageYourself() throws Exception {
        TestUser alice = registerUser();
        long aliceId = userRepository.findByEmail(alice.email()).orElseThrow().getId();

        mockMvc.perform(post("/api/dm")
                        .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverId\": %d, \"content\": \"me again\"}".formatted(aliceId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankContentIsRejectedByValidation() throws Exception {
        TestUser alice = registerUser();
        TestUser bob = registerUser();
        long bobId = userRepository.findByEmail(bob.email()).orElseThrow().getId();

        mockMvc.perform(post("/api/dm")
                        .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverId\": %d, \"content\": \"   \"}".formatted(bobId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.content").exists());
    }
}
