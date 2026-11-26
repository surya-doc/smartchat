-- ============================================================
--  V2: bring the schema in line with the JPA entities.
--  Until now these tables/columns were created by Hibernate (ddl-auto=update).
--  From here on Flyway owns the schema and Hibernate only validates it.
-- ============================================================

-- ---------- users ----------
ALTER TABLE users
    ADD COLUMN avatar_url VARCHAR(500) NULL AFTER tier,
    ADD COLUMN is_online  BOOLEAN      NOT NULL DEFAULT FALSE;

-- ---------- rooms ----------
ALTER TABLE rooms
    MODIFY COLUMN type ENUM('PUBLIC','PRIVATE','DIRECT') NOT NULL DEFAULT 'PUBLIC',
    ADD COLUMN description VARCHAR(500) NULL AFTER name,
    ADD COLUMN status ENUM('ACTIVE','ARCHIVED') NOT NULL DEFAULT 'ACTIVE' AFTER type;

-- ---------- room_members ----------
ALTER TABLE room_members
    ADD COLUMN role   ENUM('ADMIN','MEMBER')            NOT NULL DEFAULT 'MEMBER' AFTER user_id,
    ADD COLUMN status ENUM('ACTIVE','PENDING','BANNED') NOT NULL DEFAULT 'ACTIVE',
    ADD INDEX idx_room_members_user (user_id);

-- ---------- messages ----------
-- sent_at gets microsecond precision so messages sent in the same second keep their order.
ALTER TABLE messages
    ADD COLUMN reply_to_id BIGINT  NULL AFTER sender_id,
    ADD COLUMN is_edited   BOOLEAN NOT NULL DEFAULT FALSE AFTER sender_type,
    ADD COLUMN is_deleted  BOOLEAN NOT NULL DEFAULT FALSE,
    MODIFY COLUMN sent_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD CONSTRAINT fk_messages_reply_to
        FOREIGN KEY (reply_to_id) REFERENCES messages(id) ON DELETE SET NULL;

-- ---------- room_invitations ----------
CREATE TABLE room_invitations (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    token           VARCHAR(100) NOT NULL,
    room_id         BIGINT       NOT NULL,
    invited_by      BIGINT       NOT NULL,
    invited_user_id BIGINT       NULL,                 -- NULL = open link
    expires_at      DATETIME     NOT NULL,
    status          ENUM('PENDING','ACCEPTED','EXPIRED','REVOKED') NOT NULL DEFAULT 'PENDING',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_room_invitations_token (token),
    CONSTRAINT fk_room_invitations_room
        FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE,
    CONSTRAINT fk_room_invitations_invited_by
        FOREIGN KEY (invited_by) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_room_invitations_invited_user
        FOREIGN KEY (invited_user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------- join_requests ----------
CREATE TABLE join_requests (
    id           BIGINT   NOT NULL AUTO_INCREMENT,
    room_id      BIGINT   NOT NULL,
    user_id      BIGINT   NOT NULL,
    status       ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
    reviewed_by  BIGINT   NULL,
    reviewed_at  DATETIME NULL,
    requested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_join_requests_room_user (room_id, user_id),
    INDEX idx_join_requests_room_status (room_id, status),
    CONSTRAINT fk_join_requests_room
        FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE,
    CONSTRAINT fk_join_requests_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_join_requests_reviewed_by
        FOREIGN KEY (reviewed_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------- notifications ----------
CREATE TABLE notifications (
    id           BIGINT  NOT NULL AUTO_INCREMENT,
    user_id      BIGINT  NOT NULL,
    type         ENUM('JOIN_REQUEST','JOIN_APPROVED','JOIN_REJECTED',
                      'ROOM_INVITE','MESSAGE_REACTION','DIRECT_MESSAGE') NOT NULL,
    message      TEXT    NOT NULL,
    reference_id BIGINT  NULL,
    is_read      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    INDEX idx_notifications_user_read (user_id, is_read, created_at),
    CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------- message_reactions ----------
CREATE TABLE message_reactions (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    message_id BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    emoji      VARCHAR(32) NOT NULL,   -- room for multi-codepoint emoji (skin tones, ZWJ sequences)
    reacted_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_message_reactions (message_id, user_id, emoji),
    CONSTRAINT fk_message_reactions_message
        FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE,
    CONSTRAINT fk_message_reactions_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------- direct_messages ----------
CREATE TABLE direct_messages (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    sender_id   BIGINT      NOT NULL,
    receiver_id BIGINT      NOT NULL,
    content     TEXT        NOT NULL,
    is_read     BOOLEAN     NOT NULL DEFAULT FALSE,
    is_deleted  BOOLEAN     NOT NULL DEFAULT FALSE,
    sent_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    INDEX idx_dm_pair_sent (sender_id, receiver_id, sent_at),
    INDEX idx_dm_receiver_read (receiver_id, is_read),
    CONSTRAINT fk_dm_sender
        FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_dm_receiver
        FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------- seed: the bot owns #general ----------
INSERT IGNORE INTO room_members (room_id, user_id, role, status)
VALUES (1, 1, 'ADMIN', 'ACTIVE');
