package com.smartchat.smartchat.repository;

import com.smartchat.smartchat.entity.Room;
import com.smartchat.smartchat.entity.RoomMember;
import com.smartchat.smartchat.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {

    boolean existsByRoom_IdAndUser_EmailAndStatus(Long roomId, String email, RoomMember.MemberStatus status);

    boolean existsByRoom_IdAndUser_EmailAndRoleAndStatus(Long roomId, String email,
                                                          RoomMember.MemberRole role,
                                                          RoomMember.MemberStatus status);

    Optional<RoomMember> findByRoomAndUser(Room room, User user);

    @EntityGraph(attributePaths = {"room", "room.createdBy"})
    List<RoomMember> findByUser_EmailAndStatusOrderByJoinedAtAsc(String email, RoomMember.MemberStatus status);

    @EntityGraph(attributePaths = "user")
    List<RoomMember> findByRoomAndRoleAndStatus(Room room, RoomMember.MemberRole role, RoomMember.MemberStatus status);
}
