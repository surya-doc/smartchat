package com.smartchat.smartchat.service;

import com.smartchat.smartchat.dto.UserResponse;
import com.smartchat.smartchat.entity.User;
import com.smartchat.smartchat.exception.ResourceNotFoundException;
import com.smartchat.smartchat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

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

    /**
     * Cached in Redis: typing indicators and presence events need the username for an email
     * many times per second, and usernames never change, so there is nothing to invalidate.
     * (If renaming is ever added, that method must evict this entry.)
     */
    @Cacheable(cacheNames = "usernames", key = "#email")
    public String getUsername(String email) {
        return getByEmail(email).getUsername();
    }

    public List<String> getUsernames(Collection<String> emails) {
        return userRepository.findByEmailIn(emails).stream().map(User::getUsername).toList();
    }

    public UserResponse getProfile(String email) {
        User user = getByEmail(email);
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(),
                user.getTier().name(), user.getAvatarUrl());
    }
}
