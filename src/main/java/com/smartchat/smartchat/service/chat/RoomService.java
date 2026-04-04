package com.smartchat.smartchat.service.chat;

import com.smartchat.smartchat.dto.chat.CreateRoomRequest;
import com.smartchat.smartchat.dto.chat.RoomResponse;
import com.smartchat.smartchat.entity.*;
import com.smartchat.smartchat.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.messaging.simp.SimpMessageHeaderAccessor.getUser;

@Service
@RequiredArgsConstructor
@Transactional
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;
    private final RoomInvitationRepository roomInvitationRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final NotificationService notificationService;

    public RoomResponse createRoom(CreateRoomRequest request, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Room room = Room.builder()
                .name(request.getName())
                .type(request.isPrivate() ? Room.RoomType.PRIVATE : Room.RoomType.PUBLIC)
                .createdBy(user)
                .build();

        Room saved = roomRepository.save(room);

        // creator automatically joins the room
        RoomMember member = RoomMember.builder()
                .room(saved)
                .user(user)
                .role(RoomMember.MemberRole.ADMIN)  // creator is admin
                .status(RoomMember.MemberStatus.ACTIVE)  // active member
                .build();
        roomMemberRepository.save(member);

        return toResponse(saved);
    }

    public List<RoomResponse> getAllPublicRooms() {
        return roomRepository.findByType(Room.RoomType.PUBLIC)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public RoomResponse getRoomById(Long roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));
        return toResponse(room);
    }

    
    public String joinRoom(Long roomId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));
        // already a member?
        if (roomMemberRepository.existsByRoomAndUser(room, user)) {
            return "Already a member";
        }
        if (room.getType() == Room.RoomType.PRIVATE) {
            return "This is a private room. Use an invite link to join.";
        }

        if (room.getType() == Room.RoomType.PUBLIC) {
            // check if request already sent
            if (joinRequestRepository.existsByRoomAndUserAndStatus(
                    room, user, JoinRequest.JoinRequestStatus.PENDING)) {
                return "Join request already pending. Wait for admin approval.";
            }
            JoinRequest joinRequest = JoinRequest.builder()
                    .room(room)
                    .user(user)
                    .build();
            joinRequestRepository.save(joinRequest);
            return "Join request sent. Waiting for admin approval.";
        }
        // notify all admins of the room
        roomMemberRepository.findByRoomAndRole(room, RoomMember.MemberRole.ADMIN)
                .forEach(adminMember -> notificationService.notify(
                        adminMember.getUser(),
                        Notification.NotificationType.JOIN_REQUEST,
                        user.getUsername() + " wants to join \"" + room.getName() + "\"",
                        room.getId()
                ));
        return "Cannot join this room.";
    }

    
    public String generateInviteLink(Long roomId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (room.getType() != Room.RoomType.PRIVATE) {
            throw new RuntimeException("Invite links are only for private rooms.");
        }

        // only admins can generate invite links
        RoomMember member = roomMemberRepository.findByRoomAndUser(room, user)
                .orElseThrow(() -> new RuntimeException("You are not a member of this room."));

        if (member.getRole() != RoomMember.MemberRole.ADMIN) {
            throw new RuntimeException("Only admins can generate invite links.");
        }

        String token = UUID.randomUUID().toString();

        RoomInvitation invitation = RoomInvitation.builder()
                .token(token)
                .room(room)
                .invitedBy(user)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();

        roomInvitationRepository.save(invitation);

        return "http://localhost:5173/invite/" + token;
    }

    
    public RoomResponse acceptInvite(String token, String email) {
        User user = getUser(email);

        RoomInvitation invitation = roomInvitationRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid invite link."));

        if (invitation.getStatus() != RoomInvitation.InvitationStatus.PENDING) {
            throw new RuntimeException("This invite link has already been used or revoked.");
        }

        if (invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            invitation.setStatus(RoomInvitation.InvitationStatus.EXPIRED);
            roomInvitationRepository.save(invitation);
            throw new RuntimeException("This invite link has expired.");
        }

        Room room = invitation.getRoom();

        if (roomMemberRepository.existsByRoomAndUser(room, user)) {
            throw new RuntimeException("You are already a member of this room.");
        }

        // add user to room
        RoomMember member = RoomMember.builder()
                .room(room)
                .user(user)
                .role(RoomMember.MemberRole.MEMBER)
                .status(RoomMember.MemberStatus.ACTIVE)
                .build();
        roomMemberRepository.save(member);

        // mark invite as accepted
        invitation.setStatus(RoomInvitation.InvitationStatus.ACCEPTED);
        roomInvitationRepository.save(invitation);

        return toResponse(room);
    }

    // ─── new: admin approves join request ─────────────────────────────────────

    
    public void approveJoinRequest(Long roomId, Long requestId, String adminEmail) {
        User admin = getUser(adminEmail);
        Room room = getRoom(roomId);
        assertAdmin(room, admin);

        JoinRequest request = joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Join request not found."));

        if (request.getStatus() != JoinRequest.JoinRequestStatus.PENDING) {
            throw new RuntimeException("This request has already been reviewed.");
        }

        RoomMember member = RoomMember.builder()
                .room(room)
                .user(request.getUser())
                .role(RoomMember.MemberRole.MEMBER)
                .status(RoomMember.MemberStatus.ACTIVE)
                .build();
        roomMemberRepository.save(member);

        request.setStatus(JoinRequest.JoinRequestStatus.APPROVED);
        request.setReviewedBy(admin);
        request.setReviewedAt(LocalDateTime.now());
        joinRequestRepository.save(request);

        notificationService.notify(
                request.getUser(),
                Notification.NotificationType.JOIN_APPROVED,
                "Your request to join \"" + room.getName() + "\" was approved!",
                room.getId()
        );
    }

    // ─── new: admin rejects join request ──────────────────────────────────────

    public void rejectJoinRequest(Long roomId, Long requestId, String adminEmail) {
        User admin = getUser(adminEmail);
        Room room = getRoom(roomId);
        assertAdmin(room, admin);

        JoinRequest request = joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Join request not found."));

        request.setStatus(JoinRequest.JoinRequestStatus.REJECTED);
        request.setReviewedBy(admin);
        request.setReviewedAt(LocalDateTime.now());
        joinRequestRepository.save(request);

        notificationService.notify(
                request.getUser(),
                Notification.NotificationType.JOIN_REJECTED,
                "Your request to join \"" + room.getName() + "\" was rejected.",
                room.getId()
        );
    }

    // ─── new: get pending join requests (admin only) ───────────────────────────

    public List<JoinRequest> getPendingRequests(Long roomId, String adminEmail) {
        User admin = getUser(adminEmail);
        Room room = getRoom(roomId);
        assertAdmin(room, admin);
        return joinRequestRepository.findByRoomAndStatus(room, JoinRequest.JoinRequestStatus.PENDING);
    }

    private void assertAdmin(Room room, User admin) {
        RoomMember member = roomMemberRepository.findByRoomAndUser(room, admin)
                .orElseThrow(() -> new RuntimeException("You are not a member of this room."));

        if (member.getRole() != RoomMember.MemberRole.ADMIN) {
            throw new RuntimeException("Only admins can perform this action.");
        }
    }

    private Room getRoom(Long roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));
    }

    private RoomResponse toResponse(Room room) {
        return RoomResponse.builder()
                .id(room.getId())
                .name(room.getName())
                .type(room.getType().name())
                .createdBy(room.getCreatedBy().getUsername())
                .build();
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}