package com.smartchat.smartchat.service.chat;

import com.smartchat.smartchat.dto.chat.CreateRoomRequest;
import com.smartchat.smartchat.dto.chat.RoomResponse;
import com.smartchat.smartchat.entity.Room;
import com.smartchat.smartchat.entity.RoomMember;
import com.smartchat.smartchat.entity.User;
import com.smartchat.smartchat.repository.RoomMemberRepository;
import com.smartchat.smartchat.repository.RoomRepository;
import com.smartchat.smartchat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;

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

    public void joinRoom(Long roomId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (!roomMemberRepository.existsByRoomAndUser(room, user)) {
            RoomMember member = RoomMember.builder()
                    .room(room)
                    .user(user)
                    .build();
            roomMemberRepository.save(member);
        }
    }

    private RoomResponse toResponse(Room room) {
        return RoomResponse.builder()
                .id(room.getId())
                .name(room.getName())
                .type(room.getType().name())
                .createdBy(room.getCreatedBy().getUsername())
                .build();
    }
}