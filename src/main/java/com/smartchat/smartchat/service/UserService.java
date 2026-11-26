package com.smartchat.smartchat.service;

import com.smartchat.smartchat.entity.User;
import com.smartchat.smartchat.exception.ResourceNotFoundException;
import com.smartchat.smartchat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public User getByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    /** Updates the stored online flag and returns the username, if the user exists. */
    @Transactional
    public Optional<String> setOnlineStatus(String email, boolean online) {
        return userRepository.findByEmail(email).map(user -> {
            user.setIsOnline(online);   // saved by dirty checking when the transaction commits
            return user.getUsername();
        });
    }
}
