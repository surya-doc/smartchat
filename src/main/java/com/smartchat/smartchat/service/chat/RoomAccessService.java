package com.smartchat.smartchat.service.chat;

import com.smartchat.smartchat.entity.RoomMember;
import com.smartchat.smartchat.exception.ForbiddenException;
import com.smartchat.smartchat.repository.RoomMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One place for "is this user allowed in this room?". Used by REST endpoints,
 * STOMP message handlers and the STOMP SUBSCRIBE interceptor.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomAccessService {

    private final RoomMemberRepository roomMemberRepository;

    public boolean isActiveMember(Long roomId, String email) {
        return roomMemberRepository.existsByRoom_IdAndUser_EmailAndStatus(
                roomId, email, RoomMember.MemberStatus.ACTIVE);
    }

    public boolean isAdmin(Long roomId, String email) {
        return roomMemberRepository.existsByRoom_IdAndUser_EmailAndRoleAndStatus(
                roomId, email, RoomMember.MemberRole.ADMIN, RoomMember.MemberStatus.ACTIVE);
    }

    public void requireActiveMember(Long roomId, String email) {
        if (!isActiveMember(roomId, email)) {
            throw new ForbiddenException("You are not a member of this room.");
        }
    }
}
