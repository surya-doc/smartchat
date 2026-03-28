-- ============================================================
--  1. USERS
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
                                     id            BIGINT          NOT NULL AUTO_INCREMENT,
                                     username      VARCHAR(50)     NOT NULL,
    email         VARCHAR(150)    NOT NULL,
    password      VARCHAR(255)    NOT NULL,         -- BCrypt hash
    tier          ENUM('FREE','PREMIUM') NOT NULL DEFAULT 'FREE',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_users_username (username),
    UNIQUE KEY uq_users_email    (email)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
--  2. ROOMS
-- ============================================================
CREATE TABLE IF NOT EXISTS rooms (
                                     id            BIGINT          NOT NULL AUTO_INCREMENT,
                                     name          VARCHAR(100)    NOT NULL,
    type          ENUM('PUBLIC','PRIVATE') NOT NULL DEFAULT 'PUBLIC',
    created_by    BIGINT          NOT NULL,         -- FK → users.id
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_rooms_created_by
    FOREIGN KEY (created_by) REFERENCES users(id)
    ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
--  3. ROOM MEMBERS  (many-to-many: users ↔ rooms)
-- ============================================================
CREATE TABLE IF NOT EXISTS room_members (
                                            id            BIGINT          NOT NULL AUTO_INCREMENT,
                                            room_id       BIGINT          NOT NULL,         -- FK → rooms.id
                                            user_id       BIGINT          NOT NULL,         -- FK → users.id
                                            joined_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                            PRIMARY KEY (id),
    UNIQUE KEY uq_room_members (room_id, user_id),  -- no duplicate memberships
    CONSTRAINT fk_room_members_room
    FOREIGN KEY (room_id) REFERENCES rooms(id)
    ON DELETE CASCADE,
    CONSTRAINT fk_room_members_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
--  4. MESSAGES
-- ============================================================
CREATE TABLE IF NOT EXISTS messages (
                                        id            BIGINT          NOT NULL AUTO_INCREMENT,
                                        content       TEXT            NOT NULL,
                                        room_id       BIGINT          NOT NULL,         -- FK → rooms.id
                                        sender_id     BIGINT          NOT NULL,         -- FK → users.id  (bot user for AI replies)
                                        sender_type   ENUM('USER','BOT') NOT NULL DEFAULT 'USER',
    sent_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_messages_room
    FOREIGN KEY (room_id)   REFERENCES rooms(id)
    ON DELETE CASCADE,
    CONSTRAINT fk_messages_sender
    FOREIGN KEY (sender_id) REFERENCES users(id)
    ON DELETE CASCADE,

    -- speeds up "fetch last N messages for a room" query
    INDEX idx_messages_room_sent (room_id, sent_at DESC)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
--  5. PAYMENTS
-- ============================================================
CREATE TABLE IF NOT EXISTS payments (
                                        id                  BIGINT          NOT NULL AUTO_INCREMENT,
                                        user_id             BIGINT          NOT NULL,         -- FK → users.id
                                        stripe_session_id   VARCHAR(255)    NOT NULL,         -- Stripe Checkout session ID
    stripe_customer_id  VARCHAR(255)    NULL,             -- set after first payment
    status              ENUM('PENDING','COMPLETED','FAILED') NOT NULL DEFAULT 'PENDING',
    paid_at             DATETIME        NULL,             -- set when status → COMPLETED

    PRIMARY KEY (id),
    UNIQUE KEY uq_payments_stripe_session (stripe_session_id),
    CONSTRAINT fk_payments_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE,

    INDEX idx_payments_user (user_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
--  SEED DATA — system bot user + default public room
--  (required before the app starts)
-- ============================================================

-- AI bot user (sender_id for all AI replies in messages table)
INSERT IGNORE INTO users (id, username, email, password, tier)
VALUES (1, 'smartbot', 'bot@smartchat.internal', 'NOT_A_REAL_PASSWORD', 'FREE');

-- Default general room every user lands in
INSERT IGNORE INTO rooms (id, name, type, created_by)
VALUES (1, 'general', 'PUBLIC', 1);








