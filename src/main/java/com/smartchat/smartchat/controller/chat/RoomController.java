package com.smartchat.smartchat.controller.chat;

import com.smartchat.smartchat.dto.chat.CreateRoomRequest;
import com.smartchat.smartchat.dto.chat.InviteLinkResponse;
import com.smartchat.smartchat.dto.chat.JoinRequestResponse;
import com.smartchat.smartchat.dto.chat.JoinRoomResponse;
import com.smartchat.smartchat.dto.chat.MessageResponse;
import com.smartchat.smartchat.dto.chat.RoomResponse;
import com.smartchat.smartchat.service.chat.MessageService;
import com.smartchat.smartchat.service.chat.RoomService;
import com.smartchat.smartchat.service.chat.UnreadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;

/** {@code @AuthenticationPrincipal} gives the logged-in user; its "username" is the email. */
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final MessageService messageService;
    private final UnreadService unreadService;

    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody CreateRoomRequest request,
                                                   @AuthenticationPrincipal UserDetails user) {
        RoomResponse room = roomService.createRoom(request, user.getUsername());
        return ResponseEntity.created(URI.create("/api/rooms/" + room.getId())).body(room);
    }

    @GetMapping
    public List<RoomResponse> getPublicRooms() {
        return roomService.getPublicRooms();
    }

    @GetMapping("/mine")
    public List<RoomResponse> getMyRooms(@AuthenticationPrincipal UserDetails user) {
        return roomService.getMyRooms(user.getUsername());
    }

    /** roomId -> unread count for the current user (rooms with nothing unread are omitted). */
    @GetMapping("/unread")
    public Map<Long, Long> getUnreadCounts(@AuthenticationPrincipal UserDetails user) {
        return unreadService.getUnreadCounts(user.getUsername());
    }

    @PostMapping("/{roomId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRoomRead(@PathVariable Long roomId, @AuthenticationPrincipal UserDetails user) {
        unreadService.markRoomRead(roomId, user.getUsername());
    }

    @GetMapping("/{roomId}")
    public RoomResponse getRoom(@PathVariable Long roomId, @AuthenticationPrincipal UserDetails user) {
        return roomService.getRoom(roomId, user.getUsername());
    }

    @PostMapping("/{roomId}/join")
    public JoinRoomResponse joinRoom(@PathVariable Long roomId, @AuthenticationPrincipal UserDetails user) {
        return roomService.joinRoom(roomId, user.getUsername());
    }

    @GetMapping("/{roomId}/messages")
    public List<MessageResponse> getMessages(@PathVariable Long roomId, @AuthenticationPrincipal UserDetails user) {
        return messageService.getRecentMessages(roomId, user.getUsername());
    }

    @GetMapping("/{roomId}/messages/search")
    public List<MessageResponse> searchMessages(@PathVariable Long roomId,
                                                @RequestParam("q") String query,
                                                @AuthenticationPrincipal UserDetails user) {
        return messageService.searchMessages(roomId, query, user.getUsername());
    }

    // ─── invites (private rooms) ──────────────────────────────────────────────

    @PostMapping("/{roomId}/invites")
    public InviteLinkResponse createInvite(@PathVariable Long roomId, @AuthenticationPrincipal UserDetails user) {
        return roomService.createInviteLink(roomId, user.getUsername());
    }

    @PostMapping("/invites/{token}/accept")
    public RoomResponse acceptInvite(@PathVariable String token, @AuthenticationPrincipal UserDetails user) {
        return roomService.acceptInvite(token, user.getUsername());
    }

    // ─── join requests (admins of private rooms) ──────────────────────────────

    @GetMapping("/{roomId}/join-requests")
    public List<JoinRequestResponse> getPendingRequests(@PathVariable Long roomId,
                                                        @AuthenticationPrincipal UserDetails user) {
        return roomService.getPendingRequests(roomId, user.getUsername());
    }

    @PostMapping("/{roomId}/join-requests/{requestId}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approveRequest(@PathVariable Long roomId, @PathVariable Long requestId,
                               @AuthenticationPrincipal UserDetails user) {
        roomService.approveJoinRequest(roomId, requestId, user.getUsername());
    }

    @PostMapping("/{roomId}/join-requests/{requestId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rejectRequest(@PathVariable Long roomId, @PathVariable Long requestId,
                              @AuthenticationPrincipal UserDetails user) {
        roomService.rejectJoinRequest(roomId, requestId, user.getUsername());
    }
}
