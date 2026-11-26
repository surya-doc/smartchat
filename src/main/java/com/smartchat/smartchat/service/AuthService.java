package com.smartchat.smartchat.service;

import com.smartchat.smartchat.config.AppProperties;
import com.smartchat.smartchat.dto.AuthResponse;
import com.smartchat.smartchat.dto.LoginRequest;
import com.smartchat.smartchat.dto.RegisterRequest;
import com.smartchat.smartchat.entity.RoomMember;
import com.smartchat.smartchat.entity.User;
import com.smartchat.smartchat.exception.ConflictException;
import com.smartchat.smartchat.repository.RoomMemberRepository;
import com.smartchat.smartchat.repository.RoomRepository;
import com.smartchat.smartchat.repository.UserRepository;
import com.smartchat.smartchat.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final AppProperties appProperties;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        String username = request.getUsername().trim();

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email is already registered.");
        }
        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Username is already taken.");
        }

        User user = userRepository.save(User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .tier(User.UserTier.FREE)
                .build());

        joinDefaultRoom(user);
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.getEmail());

        // Throws BadCredentialsException on a wrong email or password -> 401 via GlobalExceptionHandler.
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.getPassword()));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        return toAuthResponse(user);
    }

    /** New users land in #general so the app isn't empty on first login. */
    private void joinDefaultRoom(User user) {
        Long defaultRoomId = appProperties.defaultRoomId();
        if (defaultRoomId == null) {
            return;
        }
        roomRepository.findById(defaultRoomId).ifPresent(room ->
                roomMemberRepository.save(RoomMember.builder()
                        .room(room)
                        .user(user)
                        .role(RoomMember.MemberRole.MEMBER)
                        .status(RoomMember.MemberStatus.ACTIVE)
                        .build()));
    }

    private AuthResponse toAuthResponse(User user) {
        return new AuthResponse(
                jwtTokenProvider.generateToken(user.getEmail()),
                user.getUsername(),
                user.getEmail(),
                user.getTier().name());
    }

    /** "Surya@Mail.com " and "surya@mail.com" must be the same account. */
    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
