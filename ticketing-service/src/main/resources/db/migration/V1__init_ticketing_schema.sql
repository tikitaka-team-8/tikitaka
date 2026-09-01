-- =========================================================
-- Ticketing Service initial schema
-- =========================================================

-- 회차 좌석
CREATE TABLE p_schedule_seat (
    schedule_seat_id UUID NOT NULL,
    event_session_id UUID NOT NULL,
    venue_seat_id UUID NOT NULL,
    section VARCHAR(20) NOT NULL,
    row_label VARCHAR(20) NOT NULL,
    seat_number VARCHAR(20) NOT NULL,
    seat_grade VARCHAR(30) NOT NULL,
    price BIGINT NOT NULL,
    seat_status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    CONSTRAINT pk_schedule_seat PRIMARY KEY (schedule_seat_id),
    CONSTRAINT uq_schedule_seat_session_venue_seat UNIQUE (event_session_id, venue_seat_id),
    CONSTRAINT ck_schedule_seat_price CHECK (price >= 0),
    CONSTRAINT ck_schedule_seat_status CHECK (seat_status IN ('AVAILABLE', 'HELD', 'SOLD', 'EXCLUDED'))
);

-- 회차 좌석 조회 인덱스
CREATE INDEX idx_schedule_seat_event_session ON p_schedule_seat (event_session_id);
CREATE INDEX idx_schedule_seat_event_session_status ON p_schedule_seat (event_session_id, seat_status);

-- 좌석 선점
CREATE TABLE p_seat_hold (
    seat_hold_id UUID NOT NULL,
    schedule_seat_id UUID NOT NULL,
    user_id BIGINT NOT NULL,
    hold_token UUID NOT NULL DEFAULT gen_random_uuid(),
    hold_status VARCHAR(20) NOT NULL DEFAULT 'HOLDING',
    held_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    released_at TIMESTAMPTZ,
    release_reason VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    CONSTRAINT pk_seat_hold PRIMARY KEY (seat_hold_id),
    CONSTRAINT uq_seat_hold_token UNIQUE (hold_token),
    CONSTRAINT fk_seat_hold_schedule_seat FOREIGN KEY (schedule_seat_id) REFERENCES p_schedule_seat (schedule_seat_id) ON DELETE RESTRICT,
    CONSTRAINT ck_seat_hold_expiration CHECK (held_at < expires_at),
    CONSTRAINT ck_seat_hold_status CHECK (hold_status IN ('HOLDING', 'CONFIRMED', 'EXPIRED', 'RELEASED')),
    CONSTRAINT ck_seat_hold_release_reason CHECK (release_reason IS NULL OR release_reason IN ('EXPIRED', 'USER_CANCEL', 'PAYMENT_FAILED', 'RESERVATION_CANCELED'))
);

-- 동일 좌석에는 하나의 활성 선점만 허용
CREATE UNIQUE INDEX uq_seat_hold_active_schedule_seat ON p_seat_hold (schedule_seat_id) WHERE hold_status = 'HOLDING';

-- 좌석 선점 조회 및 만료 처리 인덱스
CREATE INDEX idx_seat_hold_schedule_seat ON p_seat_hold (schedule_seat_id);
CREATE INDEX idx_seat_hold_status_expires_at ON p_seat_hold (hold_status, expires_at);

-- 예매
CREATE TABLE p_reservation (
    reservation_id UUID NOT NULL,
    user_id BIGINT NOT NULL,
    event_id UUID NOT NULL,
    event_session_id UUID NOT NULL,
    payment_id UUID,
    reservation_number VARCHAR(30) NOT NULL,
    event_title VARCHAR(200) NOT NULL,
    session_start_at TIMESTAMPTZ NOT NULL,
    seat_count INTEGER NOT NULL,
    total_amount BIGINT NOT NULL,
    reservation_status VARCHAR(30) NOT NULL DEFAULT 'PAYMENT_PENDING',
    failure_reason VARCHAR(30),
    payment_completed_at TIMESTAMPTZ,
    idempotency_key VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_reservation PRIMARY KEY (reservation_id),
    CONSTRAINT uq_reservation_number UNIQUE (reservation_number),
    CONSTRAINT uq_reservation_user_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_reservation_seat_count CHECK (seat_count > 0),
    CONSTRAINT ck_reservation_total_amount CHECK (total_amount >= 0),
    CONSTRAINT ck_reservation_status CHECK (reservation_status IN ('PAYMENT_PENDING', 'PAYMENT_PROCESSING', 'CONFIRMED', 'FAILED', 'CANCEL_PENDING', 'CANCELLED')),
    CONSTRAINT ck_reservation_failure_reason CHECK (failure_reason IS NULL OR failure_reason IN ('SEAT_HOLD_EXPIRED', 'PAYMENT_TIMEOUT', 'PAYMENT_FAILED'))
);

-- 예매 조회 인덱스
CREATE INDEX idx_reservation_user_created_at ON p_reservation (user_id, created_at DESC) WHERE is_deleted = FALSE;
CREATE INDEX idx_reservation_event_session ON p_reservation (event_session_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_reservation_user_status ON p_reservation (user_id, reservation_status) WHERE is_deleted = FALSE;
CREATE INDEX idx_reservation_payment ON p_reservation (payment_id) WHERE payment_id IS NOT NULL;

-- 예매 좌석
CREATE TABLE p_reservation_seats (
    reservation_seat_id UUID NOT NULL,
    reservation_id UUID NOT NULL,
    seat_hold_id UUID NOT NULL,
    schedule_seat_id UUID NOT NULL,
    price BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_reservation_seats PRIMARY KEY (reservation_seat_id),
    CONSTRAINT uq_reservation_seats_seat_hold UNIQUE (seat_hold_id),
    CONSTRAINT fk_reservation_seats_reservation FOREIGN KEY (reservation_id) REFERENCES p_reservation (reservation_id) ON DELETE RESTRICT,
    CONSTRAINT fk_reservation_seats_seat_hold FOREIGN KEY (seat_hold_id) REFERENCES p_seat_hold (seat_hold_id) ON DELETE RESTRICT,
    CONSTRAINT fk_reservation_seats_schedule_seat FOREIGN KEY (schedule_seat_id) REFERENCES p_schedule_seat (schedule_seat_id) ON DELETE RESTRICT,
    CONSTRAINT ck_reservation_seats_price CHECK (price >= 0)
);

-- 예매 좌석 조회 인덱스
CREATE INDEX idx_reservation_seats_reservation ON p_reservation_seats (reservation_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_reservation_seats_schedule_seat ON p_reservation_seats (schedule_seat_id) WHERE is_deleted = FALSE;
