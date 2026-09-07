-- 예매 결과 이벤트 처리 이력
CREATE TABLE p_notification_inbox (
    event_id UUID NOT NULL,
    reservation_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_notification_inbox
        PRIMARY KEY (event_id),

    CONSTRAINT ck_notification_inbox_event_type
        CHECK (
            event_type IN (
                'RESERVATION_CONFIRMED',
                'RESERVATION_FAILED'
            )
        )
);
