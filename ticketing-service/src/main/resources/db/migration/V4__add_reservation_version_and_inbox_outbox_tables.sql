ALTER TABLE p_reservation
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE p_reservation_inbox (
    event_id              UUID            NOT NULL,
    reservation_id        UUID            NOT NULL,
    event_type            VARCHAR(100)    NOT NULL,
    processed_at          TIMESTAMPTZ     NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_reservation_inbox
        PRIMARY KEY (event_id),

    CONSTRAINT ck_reservation_inbox_event_type
        CHECK (
            event_type IN (
                'PAYMENT_SUCCEEDED',
                'PAYMENT_FAILED'
            )
        )
);

CREATE TABLE p_reservation_outbox (
    event_id              UUID            NOT NULL,
    reservation_id        UUID            NOT NULL,
    event_type            VARCHAR(100)    NOT NULL,
    payload               TEXT            NOT NULL,
    status                VARCHAR(30)     NOT NULL
        DEFAULT 'PENDING',
    created_at            TIMESTAMPTZ     NOT NULL
        DEFAULT CURRENT_TIMESTAMP,
    published_at          TIMESTAMPTZ,

    CONSTRAINT pk_reservation_outbox
        PRIMARY KEY (event_id),

    CONSTRAINT ck_reservation_outbox_event_type
        CHECK (
            event_type IN (
                'RESERVATION_CONFIRMED',
                'RESERVATION_FAILED'
            )
        ),

    CONSTRAINT ck_reservation_outbox_status
        CHECK (
            status IN (
                'PENDING',
                'PUBLISHED'
            )
        )
);

CREATE INDEX idx_reservation_outbox_status_created_at
    ON p_reservation_outbox (status, created_at);
