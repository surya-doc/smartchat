package com.smartchat.smartchat.service;

import com.smartchat.smartchat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;

    public void setOnlineStatus(String email, boolean isOnline) {
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setIsOnline(isOnline);
            userRepository.save(user);
        });
    }
}