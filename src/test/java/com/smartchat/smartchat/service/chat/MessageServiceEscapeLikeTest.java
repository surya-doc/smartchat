package com.smartchat.smartchat.service.chat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageServiceEscapeLikeTest {

    @Test
    void escapesWildcardsAndTheEscapeCharacter() {
        assertThat(MessageService.escapeLike("100%")).isEqualTo("100!%");
        assertThat(MessageService.escapeLike("snake_case")).isEqualTo("snake!_case");
        assertThat(MessageService.escapeLike("wow!")).isEqualTo("wow!!");
        assertThat(MessageService.escapeLike("plain text")).isEqualTo("plain text");
    }
}
