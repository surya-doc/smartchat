package com.smartchat.smartchat.api;

import com.smartchat.smartchat.IntegrationTestBase;
import com.smartchat.smartchat.dto.chat.MessageResponse;
import com.smartchat.smartchat.dto.chat.SendMessageRequest;
import com.smartchat.smartchat.exception.BadRequestException;
import com.smartchat.smartchat.exception.ForbiddenException;
import com.smartchat.smartchat.service.chat.MessageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * New messages arrive over STOMP, which calls MessageService.sendMessage, so these tests
 * call the service directly for sending and use the REST API for everything else.
 */
class MessageApiTests extends IntegrationTestBase {

    @Autowired
    private MessageService messageService;

    @Test
    void membersSeeMessagesNewestFirst() throws Exception {
        TestUser owner = registerUser();
        long roomId = createRoom(owner, "Chatty", false);

        messageService.sendMessage(new SendMessageRequest(roomId, "first", null), owner.email());
        messageService.sendMessage(new SendMessageRequest(roomId, "second", null), owner.email());

        mockMvc.perform(get("/api/rooms/{id}/messages", roomId).header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].content").value("second"))
                .andExpect(jsonPath("$[0].senderUsername").value(owner.username()));
    }

    @Test
    void nonMembersCannotSend() throws Exception {
        TestUser owner = registerUser();
        TestUser outsider = registerUser();
        long roomId = createRoom(owner, "Closed door", false);

        assertThatThrownBy(() -> messageService.sendMessage(
                new SendMessageRequest(roomId, "let me in", null), outsider.email()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void replyMustBeInTheSameRoom() throws Exception {
        TestUser owner = registerUser();
        long roomA = createRoom(owner, "Room A", false);
        long roomB = createRoom(owner, "Room B", false);
        MessageResponse inA = messageService.sendMessage(new SendMessageRequest(roomA, "secret in A", null), owner.email());

        assertThatThrownBy(() -> messageService.sendMessage(
                new SendMessageRequest(roomB, "quoting A", inA.getId()), owner.email()))
                .isInstanceOf(BadRequestException.class);

        MessageResponse reply = messageService.sendMessage(
                new SendMessageRequest(roomA, "reply", inA.getId()), owner.email());
        assertThat(reply.getReplyToContent()).isEqualTo("secret in A");
    }

    @Test
    void onlyTheSenderCanEdit() throws Exception {
        TestUser owner = registerUser();
        TestUser other = registerUser();
        long roomId = createRoom(owner, "Editors", false);
        mockMvc.perform(post("/api/rooms/{id}/join", roomId).header(HttpHeaders.AUTHORIZATION, other.bearer()));
        MessageResponse message = messageService.sendMessage(new SendMessageRequest(roomId, "typo", null), owner.email());

        mockMvc.perform(put("/api/messages/{id}", message.getId())
                        .header(HttpHeaders.AUTHORIZATION, other.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"hacked\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/messages/{id}", message.getId())
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"fixed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("fixed"))
                .andExpect(jsonPath("$.isEdited").value(true));
    }

    @Test
    void roomAdminCanDeleteAnyMessage() throws Exception {
        TestUser admin = registerUser();
        TestUser member = registerUser();
        long roomId = createRoom(admin, "Moderated", false);
        mockMvc.perform(post("/api/rooms/{id}/join", roomId).header(HttpHeaders.AUTHORIZATION, member.bearer()));
        MessageResponse spam = messageService.sendMessage(new SendMessageRequest(roomId, "spam", null), member.email());

        mockMvc.perform(delete("/api/messages/{id}", spam.getId()).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDeleted").value(true))
                .andExpect(jsonPath("$.content").value("This message was deleted."));
    }

    @Test
    void reactionsToggleOnAndOff() throws Exception {
        TestUser owner = registerUser();
        long roomId = createRoom(owner, "Reactions", false);
        MessageResponse message = messageService.sendMessage(new SendMessageRequest(roomId, "nice", null), owner.email());

        mockMvc.perform(post("/api/messages/{id}/reactions", message.getId())
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emoji\": \"👍\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reactions['👍']").value(1));

        mockMvc.perform(post("/api/messages/{id}/reactions", message.getId())
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emoji\": \"👍\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reactions['👍']").doesNotExist());
    }

    @Test
    void searchTreatsPercentAndUnderscoreLiterally() throws Exception {
        TestUser owner = registerUser();
        long roomId = createRoom(owner, "Search room", false);
        messageService.sendMessage(new SendMessageRequest(roomId, "I am 100% sure", null), owner.email());
        messageService.sendMessage(new SendMessageRequest(roomId, "1000 reasons", null), owner.email());

        mockMvc.perform(get("/api/rooms/{id}/messages/search", roomId)
                        .param("q", "00%")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].content").value("I am 100% sure"));

        mockMvc.perform(get("/api/rooms/{id}/messages/search", roomId)
                        .param("q", "x")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isBadRequest());
    }
}
