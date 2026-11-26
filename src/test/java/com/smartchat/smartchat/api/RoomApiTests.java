package com.smartchat.smartchat.api;

import com.jayway.jsonpath.JsonPath;
import com.smartchat.smartchat.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoomApiTests extends IntegrationTestBase {

    @Test
    void privateFlagIsRespectedWhenCreatingARoom() throws Exception {
        TestUser owner = registerUser();
        long roomId = createRoom(owner, "Secret plans", true);

        mockMvc.perform(get("/api/rooms/{id}", roomId).header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("PRIVATE"))
                .andExpect(jsonPath("$.createdBy").value(owner.username()));
    }

    @Test
    void privateRoomsAreInvisibleToOutsiders() throws Exception {
        TestUser owner = registerUser();
        TestUser outsider = registerUser();
        long roomId = createRoom(owner, "Hidden", true);

        mockMvc.perform(get("/api/rooms/{id}", roomId).header(HttpHeaders.AUTHORIZATION, outsider.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/rooms/{id}/messages", roomId).header(HttpHeaders.AUTHORIZATION, outsider.bearer()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/rooms").header(HttpHeaders.AUTHORIZATION, outsider.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem((int) roomId))));
    }

    @Test
    void anyoneCanJoinAPublicRoomInstantly() throws Exception {
        TestUser owner = registerUser();
        TestUser joiner = registerUser();
        long roomId = createRoom(owner, "Open lounge", false);

        mockMvc.perform(get("/api/rooms/{id}/messages", roomId).header(HttpHeaders.AUTHORIZATION, joiner.bearer()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/rooms/{id}/join", roomId).header(HttpHeaders.AUTHORIZATION, joiner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("JOINED"));

        mockMvc.perform(post("/api/rooms/{id}/join", roomId).header(HttpHeaders.AUTHORIZATION, joiner.bearer()))
                .andExpect(jsonPath("$.status").value("ALREADY_MEMBER"));

        mockMvc.perform(get("/api/rooms/{id}/messages", roomId).header(HttpHeaders.AUTHORIZATION, joiner.bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void privateRoomJoinRequestCanBeApprovedByAdmin() throws Exception {
        TestUser admin = registerUser();
        TestUser requester = registerUser();
        long roomId = createRoom(admin, "Members only", true);

        mockMvc.perform(post("/api/rooms/{id}/join", roomId).header(HttpHeaders.AUTHORIZATION, requester.bearer()))
                .andExpect(jsonPath("$.status").value("REQUESTED"));
        mockMvc.perform(post("/api/rooms/{id}/join", roomId).header(HttpHeaders.AUTHORIZATION, requester.bearer()))
                .andExpect(jsonPath("$.status").value("REQUEST_PENDING"));

        // the requester can't see the queue, the admin can
        mockMvc.perform(get("/api/rooms/{id}/join-requests", roomId).header(HttpHeaders.AUTHORIZATION, requester.bearer()))
                .andExpect(status().isForbidden());
        long requestId = firstPendingRequestId(admin, roomId);

        mockMvc.perform(post("/api/rooms/{id}/join-requests/{req}/approve", roomId, requestId)
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/rooms/{id}/messages", roomId).header(HttpHeaders.AUTHORIZATION, requester.bearer()))
                .andExpect(status().isOk());

        // approving twice is a conflict, not a second membership
        mockMvc.perform(post("/api/rooms/{id}/join-requests/{req}/approve", roomId, requestId)
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isConflict());
    }

    @Test
    void declinedUserCanAskAgain() throws Exception {
        TestUser admin = registerUser();
        TestUser requester = registerUser();
        long roomId = createRoom(admin, "Picky club", true);

        mockMvc.perform(post("/api/rooms/{id}/join", roomId).header(HttpHeaders.AUTHORIZATION, requester.bearer()));
        long requestId = firstPendingRequestId(admin, roomId);
        mockMvc.perform(post("/api/rooms/{id}/join-requests/{req}/reject", roomId, requestId)
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/rooms/{id}/join", roomId).header(HttpHeaders.AUTHORIZATION, requester.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    void adminCannotApproveARequestThatBelongsToAnotherRoom() throws Exception {
        TestUser admin = registerUser();
        TestUser requester = registerUser();
        long roomA = createRoom(admin, "Room A", true);
        long roomB = createRoom(admin, "Room B", true);

        mockMvc.perform(post("/api/rooms/{id}/join", roomB).header(HttpHeaders.AUTHORIZATION, requester.bearer()));
        long requestForB = firstPendingRequestId(admin, roomB);

        mockMvc.perform(post("/api/rooms/{id}/join-requests/{req}/approve", roomA, requestForB)
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/rooms/{id}/messages", roomA).header(HttpHeaders.AUTHORIZATION, requester.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void inviteLinkLetsSomeoneJoinOnce() throws Exception {
        TestUser admin = registerUser();
        TestUser guest = registerUser();
        TestUser lateGuest = registerUser();
        long roomId = createRoom(admin, "Invite only", true);

        String json = mockMvc.perform(post("/api/rooms/{id}/invites", roomId).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", startsWith("http://localhost:4200/invite/")))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(json, "$.token");

        mockMvc.perform(post("/api/rooms/invites/{token}/accept", token).header(HttpHeaders.AUTHORIZATION, guest.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) roomId));
        mockMvc.perform(get("/api/rooms/{id}/messages", roomId).header(HttpHeaders.AUTHORIZATION, guest.bearer()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/rooms/invites/{token}/accept", token).header(HttpHeaders.AUTHORIZATION, lateGuest.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyAdminsCanCreateInviteLinks() throws Exception {
        TestUser admin = registerUser();
        TestUser member = registerUser();
        long roomId = createRoom(admin, "Admins invite", true);

        String json = mockMvc.perform(post("/api/rooms/{id}/invites", roomId).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/rooms/invites/{token}/accept", (String) JsonPath.read(json, "$.token"))
                .header(HttpHeaders.AUTHORIZATION, member.bearer()));

        mockMvc.perform(post("/api/rooms/{id}/invites", roomId).header(HttpHeaders.AUTHORIZATION, member.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownRoomIs404WithProblemDetailBody() throws Exception {
        TestUser user = registerUser();

        mockMvc.perform(get("/api/rooms/{id}", 999_999).header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not found"))
                .andExpect(jsonPath("$.detail").value("Room not found"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
