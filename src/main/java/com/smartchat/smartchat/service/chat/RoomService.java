package com.smartchat.smartchat.service.chat;

import com.smartchat.smartchat.config.AppProperties;
import com.smartchat.smartchat.dto.chat.CreateRoomRequest;
import com.smartchat.smartchat.dto.chat.InviteLinkResponse;
import com.smartchat.smartchat.dto.chat.JoinRequestResponse;
import com.smartchat.smartchat.dto.chat.JoinRoomResponse;
import com.smartchat.smartchat.dto.chat.RoomResponse;
import com.smartchat.smartchat.entity.JoinRequest;
import com.smartchat.smartchat.entity.Notification;
import com.smartchat.smartchat.entity.Room;
import com.smartchat.smartchat.entity.RoomInvitation;
import com.smartchat.smartchat.entity.RoomMember;
import com.smartchat.smartchat.entity.User;
import com.smartchat.smartchat.exception.BadRequestException;
import com.smartchat.smartchat.exception.ConflictException;
import com.smartchat.smartchat.exception.ForbiddenException;
import com.smartchat.smartchat.exception.ResourceNotFoundException;
import com.smartchat.smartchat.repository.JoinRequestRepository;
import com.smartchat.smartchat.repository.RoomInvitationRepository;
import com.smartchat.smartchat.repository.RoomMemberRepository;
import com.smartchat.smartchat.repository.RoomRepository;
import com.smartchat.smartchat.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.smartchat.smartchat.entity.RoomMember.MemberRole.ADMIN;
import static com.smartchat.smartchat.entity.RoomMember.MemberRole.MEMBER;
import static com.smartchat.smartchat.entity.RoomMember.MemberStatus.ACTIVE;
import static com.smartchat.smartchat.entity.RoomMember.MemberStatus.BANNED;

/**
 * Rooms and membership.
 * <ul>
 *   <li>PUBLIC rooms: anyone can join instantly.</li>
 *   <li>PRIVATE rooms: join with an invite link, or send a join request that an admin approves.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RoomService {

    private static final Duration INVITE_TTL = Duration.ofHours(24);

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoomInvitationRepository roomInvitationRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final UserService userService;
    private final RoomAccessService roomAccessService;
    private final NotificationService notificationService;
    private final AppProperties appProperties;

    public RoomResponse createRoom(CreateRoomRequest request, String email) {
        User creator = userService.getByEmail(email);

        Room room = roomRepository.save(Room.builder()
                .name(request.name().trim())
                .description(request.description())
                .type(request.isPrivate() ? Room.RoomType.PRIVATE : Room.RoomType.PUBLIC)
                .createdBy(creator)
                .build());

        addMember(room, creator, ADMIN);
        return toResponse(room);
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> getPublicRooms() {
        return roomRepository.findByTypeAndStatusOrderByNameAsc(Room.RoomType.PUBLIC, Room.RoomStatus.ACTIVE)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> getMyRooms(String email) {
        return roomMemberRepository.findByUser_EmailAndStatusOrderByJoinedAtAsc(email, ACTIVE)
                .stream()
                .map(RoomMember::getRoom)
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RoomResponse getRoom(Long roomId, String email) {
        Room room = getRoomEntity(roomId);
        // Private rooms are invisible to non-members: 404 instead of 403 so we don't reveal they exist.
        if (room.getType() != Room.RoomType.PUBLIC && !roomAccessService.isActiveMember(roomId, email)) {
            throw new ResourceNotFoundException("Room not found");
        }
        return toResponse(room);
    }

    public JoinRoomResponse joinRoom(Long roomId, String email) {
        User user = userService.getByEmail(email);
        Room room = getRoomEntity(roomId);

        if (room.getStatus() != Room.RoomStatus.ACTIVE) {
            throw new BadRequestException("This room is archived.");
        }

        Optional<RoomMember> existing = roomMemberRepository.findByRoomAndUser(room, user);
        if (existing.isPresent() && existing.get().getStatus() == BANNED) {
            throw new ForbiddenException("You are banned from this room.");
        }
        if (existing.isPresent() && existing.get().getStatus() == ACTIVE) {
            return new JoinRoomResponse(JoinRoomResponse.Status.ALREADY_MEMBER, "You are already a member of this room.");
        }

        return switch (room.getType()) {
            case PUBLIC -> {
                addMember(room, user, MEMBER);
                yield new JoinRoomResponse(JoinRoomResponse.Status.JOINED, "Joined " + room.getName() + ".");
            }
            case PRIVATE -> requestToJoin(room, user);
            case DIRECT -> throw new ForbiddenException("Direct conversations can't be joined.");
        };
    }

    public InviteLinkResponse createInviteLink(Long roomId, String email) {
        User user = userService.getByEmail(email);
        Room room = getRoomEntity(roomId);

        if (room.getType() != Room.RoomType.PRIVATE) {
            throw new BadRequestException("Invite links are only for private rooms.");
        }
        requireAdmin(room, email);

        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plus(INVITE_TTL);

        roomInvitationRepository.save(RoomInvitation.builder()
                .token(token)
                .room(room)
                .invitedBy(user)
                .expiresAt(expiresAt)
                .build());

        return new InviteLinkResponse(appProperties.frontendUrl() + "/invite/" + token, token, expiresAt);
    }

    public RoomResponse acceptInvite(String token, String email) {
        User user = userService.getByEmail(email);
        RoomInvitation invitation = roomInvitationRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("This invite link is invalid."));

        if (invitation.getStatus() != RoomInvitation.InvitationStatus.PENDING) {
            throw new BadRequestException("This invite link has already been used or revoked.");
        }
        if (invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("This invite link has expired.");
        }
        if (invitation.getInvitedUser() != null && !invitation.getInvitedUser().getId().equals(user.getId())) {
            throw new ForbiddenException("This invite was sent to someone else.");
        }

        Room room = invitation.getRoom();
        Optional<RoomMember> existing = roomMemberRepository.findByRoomAndUser(room, user);
        if (existing.isPresent() && existing.get().getStatus() == ACTIVE) {
            throw new ConflictException("You are already a member of this room.");
        }
        if (existing.isPresent() && existing.get().getStatus() == BANNED) {
            throw new ForbiddenException("You are banned from this room.");
        }

        addMember(room, user, MEMBER);
        invitation.setStatus(RoomInvitation.InvitationStatus.ACCEPTED); // saved by dirty checking
        return toResponse(room);
    }

    @Transactional(readOnly = true)
    public List<JoinRequestResponse> getPendingRequests(Long roomId, String adminEmail) {
        Room room = getRoomEntity(roomId);
        requireAdmin(room, adminEmail);

        return joinRequestRepository
                .findByRoomAndStatusOrderByRequestedAtAsc(room, JoinRequest.JoinRequestStatus.PENDING)
                .stream()
                .map(request -> new JoinRequestResponse(
                        request.getId(),
                        request.getUser().getId(),
                        request.getUser().getUsername(),
                        request.getRequestedAt()))
                .toList();
    }

    public void approveJoinRequest(Long roomId, Long requestId, String adminEmail) {
        User admin = userService.getByEmail(adminEmail);
        Room room = getRoomEntity(roomId);
        requireAdmin(room, adminEmail);

        JoinRequest request = getPendingRequest(room, requestId);
        addMember(room, request.getUser(), MEMBER);
        markReviewed(request, JoinRequest.JoinRequestStatus.APPROVED, admin);

        notificationService.notify(request.getUser(), Notification.NotificationType.JOIN_APPROVED,
                "Your request to join \"" + room.getName() + "\" was approved.", room.getId());
    }

    public void rejectJoinRequest(Long roomId, Long requestId, String adminEmail) {
        User admin = userService.getByEmail(adminEmail);
        Room room = getRoomEntity(roomId);
        requireAdmin(room, adminEmail);

        JoinRequest request = getPendingRequest(room, requestId);
        markReviewed(request, JoinRequest.JoinRequestStatus.REJECTED, admin);

        notificationService.notify(request.getUser(), Notification.NotificationType.JOIN_REJECTED,
                "Your request to join \"" + room.getName() + "\" was declined.", room.getId());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private JoinRoomResponse requestToJoin(Room room, User user) {
        JoinRequest request = joinRequestRepository.findByRoomAndUser(room, user).orElse(null);

        if (request != null && request.getStatus() == JoinRequest.JoinRequestStatus.PENDING) {
            return new JoinRoomResponse(JoinRoomResponse.Status.REQUEST_PENDING, "Your request is waiting for an admin.");
        }

        if (request == null) {
            request = JoinRequest.builder().room(room).user(user).build();
        } else {
            // One row per (room, user): reuse it so someone who was declined can ask again.
            request.setStatus(JoinRequest.JoinRequestStatus.PENDING);
            request.setReviewedBy(null);
            request.setReviewedAt(null);
            request.setRequestedAt(LocalDateTime.now());
        }
        joinRequestRepository.save(request);

        String text = user.getUsername() + " wants to join \"" + room.getName() + "\"";
        roomMemberRepository.findByRoomAndRoleAndStatus(room, ADMIN, ACTIVE)
                .forEach(admin -> notificationService.notify(
                        admin.getUser(), Notification.NotificationType.JOIN_REQUEST, text, room.getId()));

        return new JoinRoomResponse(JoinRoomResponse.Status.REQUESTED, "Request sent. An admin will review it.");
    }

    /** The request must belong to this room, otherwise an admin of room A could approve requests for room B. */
    private JoinRequest getPendingRequest(Room room, Long requestId) {
        JoinRequest request = joinRequestRepository.findById(requestId)
                .filter(r -> r.getRoom().getId().equals(room.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Join request not found."));

        if (request.getStatus() != JoinRequest.JoinRequestStatus.PENDING) {
            throw new ConflictException("This request has already been reviewed.");
        }
        return request;
    }

    private void markReviewed(JoinRequest request, JoinRequest.JoinRequestStatus status, User admin) {
        request.setStatus(status);
        request.setReviewedBy(admin);
        request.setReviewedAt(LocalDateTime.now());
    }

    /** Creates the membership, or re-activates an old one (e.g. someone who left and came back). */
    private void addMember(Room room, User user, RoomMember.MemberRole role) {
        RoomMember member = roomMemberRepository.findByRoomAndUser(room, user)
                .orElseGet(() -> RoomMember.builder().room(room).user(user).build());
        member.setRole(role);
        member.setStatus(ACTIVE);
        roomMemberRepository.save(member);
    }

    private void requireAdmin(Room room, String email) {
        if (!roomAccessService.isActiveMember(room.getId(), email)) {
            throw new ForbiddenException("You are not a member of this room.");
        }
        if (!roomAccessService.isAdmin(room.getId(), email)) {
            throw new ForbiddenException("Only room admins can do this.");
        }
    }

    private Room getRoomEntity(Long roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));
    }

    private RoomResponse toResponse(Room room) {
        return RoomResponse.builder()
                .id(room.getId())
                .name(room.getName())
                .description(room.getDescription())
                .type(room.getType().name())
                .createdBy(room.getCreatedBy().getUsername())
                .build();
    }
}
