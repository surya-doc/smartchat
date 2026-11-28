-- ============================================================
--  V4: transactional outbox.
--  Domain events are written here in the SAME transaction as the change that caused them,
--  then OutboxPublisher sends them to Kafka. This avoids the "dual write" problem:
--  saving to MySQL and sending to Kafka as two separate steps can't be atomic.
-- ============================================================
CREATE TABLE outbox_events (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    event_id     VARCHAR(36)  NOT NULL,          -- UUID; consumers use it to drop duplicates
    event_type   VARCHAR(50)  NOT NULL,
    topic        VARCHAR(100) NOT NULL,
    message_key  VARCHAR(64)  NOT NULL,          -- Kafka key: same key -> same partition -> ordered
    payload      TEXT         NOT NULL,          -- JSON
    attempts     INT          NOT NULL DEFAULT 0,
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at DATETIME(6)  NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uq_outbox_event_id (event_id),
    INDEX idx_outbox_unpublished (published_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
