package com.smartchat.smartchat.controller.chat;

import com.smartchat.smartchat.dto.chat.CreateRoomRequest;
import com.smartchat.smartchat.dto.chat.MessageResponse;
import com.smartchat.smartchat.dto.chat.RoomResponse;
import com.smartchat.smartchat.entity.JoinRequest;
import com.smartchat.smartchat.service.chat.MessageService;
import com.smartchat.smartchat.service.chat.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final MessageService messageService;

    // ─── existing ─────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(
            @Valid @RequestBody CreateRoomRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(roomService.createRoom(request, userDetails.getUsername()));
    }

    @GetMapping
    public ResponseEntity<List<RoomResponse>> getAllRooms() {
        return ResponseEntity.ok(roomService.getAllPublicRooms());
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<RoomResponse> getRoom(@PathVariable Long roomId) {
        return ResponseEntity.ok(roomService.getRoomById(roomId));
    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<String> joinRoom(
            @PathVariable Long roomId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(roomService.joinRoom(roomId, userDetails.getUsername()));
    }

    @GetMapping("/{roomId}/messages")
    public ResponseEntity<List<MessageResponse>> getMessages(@PathVariable Long roomId) {
        return ResponseEntity.ok(messageService.getLast50Messages(roomId));
    }

    // ─── new: invite link ─────────────────────────────────────────────────────

    @PostMapping("/{roomId}/invite")
    public ResponseEntity<String> generateInvite(
            @PathVariable Long roomId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(roomService.generateInviteLink(roomId, userDetails.getUsername()));
    }

    @GetMapping("/invite/{token}")
    public ResponseEntity<RoomResponse> acceptInvite(
            @PathVariable String token,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(roomService.acceptInvite(token, userDetails.getUsername()));
    }

    // ─── new: join requests (admin) ───────────────────────────────────────────

    @GetMapping("/{roomId}/join-requests")
    public ResponseEntity<List<JoinRequest>> getPendingRequests(
            @PathVariable Long roomId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(roomService.getPendingRequests(roomId, userDetails.getUsername()));
    }

    @PostMapping("/{roomId}/join-requests/{requestId}/approve")
    public ResponseEntity<Void> approveRequest(
            @PathVariable Long roomId,
            @PathVariable Long requestId,
            @AuthenticationPrincipal UserDetails userDetails) {
        roomService.approveJoinRequest(roomId, requestId, userDetails.getUsername());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{roomId}/join-requests/{requestId}/reject")
    public ResponseEntity<Void> rejectRequest(
            @PathVariable Long roomId,
            @PathVariable Long requestId,
            @AuthenticationPrincipal UserDetails userDetails) {
        roomService.rejectJoinRequest(roomId, requestId, userDetails.getUsername());
        return ResponseEntity.ok().build();
    }

    // ─── new: edit message ─────────────────────────────────────────────────────

    @PutMapping("/messages/{messageId}")
    public ResponseEntity<MessageResponse> editMessage(
            @PathVariable Long messageId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                messageService.editMessage(messageId, body.get("content"), userDetails.getUsername()));
    }

// ─── new: delete message ───────────────────────────────────────────────────

    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<MessageResponse> deleteMessage(
            @PathVariable Long messageId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                messageService.deleteMessage(messageId, userDetails.getUsername()));
    }

// ─── new: toggle reaction ──────────────────────────────────────────────────

    @PostMapping("/messages/{messageId}/react")
    public ResponseEntity<Map<String, Long>> reactToMessage(
            @PathVariable Long messageId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                messageService.toggleReaction(messageId, body.get("emoji"), userDetails.getUsername()));
    }

    @GetMapping("/{roomId}/messages/search")
    public ResponseEntity<List<MessageResponse>> searchMessages(
            @PathVariable Long roomId,
            @RequestParam String q) {
        return ResponseEntity.ok(messageService.searchMessages(roomId, q));
    }
}