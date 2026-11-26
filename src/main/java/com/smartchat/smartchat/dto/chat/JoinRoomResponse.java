package com.smartchat.smartchat.dto.chat;

public record JoinRoomResponse(Status status, String message) {

    public enum Status {
        JOINED,           // public room: joined immediately
        ALREADY_MEMBER,
        REQUESTED,        // private room: request sent to the admins
        REQUEST_PENDING   // private room: a request is already waiting
    }
}
