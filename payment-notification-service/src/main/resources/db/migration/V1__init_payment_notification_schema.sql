-- =========================================================
-- Payment & Notification Service initial schema
-- =========================================================

-- 결제
CREATE TABLE p_payment (
    payment_id UUID NOT NULL,
    reservation_id UUID NOT NULL,
    user_id BIGINT NOT NULL,
    order_id VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    amount BIGINT NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'KRW',
    payment_method VARCHAR(30),
    payment_provider VARCHAR(30) NOT NULL,
    pg_payment_key VARCHAR(200),
    status VARCHAR(30) NOT NULL DEFAULT 'READY',
    failure_code VARCHAR(100),
    failure_reason TEXT,
    requested_at TIMESTAMPTZ NOT NULL,
    approved_at TIMESTAMPTZ,
    canceled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_payment PRIMARY KEY (payment_id),
    CONSTRAINT uq_payment_order_id UNIQUE (order_id),
    CONSTRAINT uq_payment_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_payment_amount CHECK (amount >= 0),
    CONSTRAINT ck_payment_status CHECK (status IN ('READY', 'PROCESSING', 'APPROVED', 'FAILED', 'UNKNOWN', 'CANCELED'))
);

-- 결제 조회 인덱스
CREATE INDEX idx_payment_reservation_id ON p_payment (reservation_id);
CREATE INDEX idx_payment_user_id ON p_payment (user_id);
CREATE INDEX idx_payment_status ON p_payment (status);

-- 결제 처리 이력
CREATE TABLE p_payment_transaction (
    transaction_id UUID NOT NULL,
    payment_id UUID NOT NULL,
    transaction_type VARCHAR(30) NOT NULL,
    provider VARCHAR(30) NOT NULL,
    pg_transaction_id VARCHAR(200),
    amount BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempt_no INTEGER NOT NULL,
    failure_code VARCHAR(100),
    failure_reason TEXT,
    requested_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_payment_transaction PRIMARY KEY (transaction_id),
    CONSTRAINT fk_payment_transaction_payment FOREIGN KEY (payment_id) REFERENCES p_payment (payment_id) ON DELETE RESTRICT,
    CONSTRAINT uq_payment_transaction_attempt UNIQUE (payment_id, transaction_type, attempt_no),
    CONSTRAINT ck_payment_transaction_amount CHECK (amount >= 0),
    CONSTRAINT ck_payment_transaction_attempt_no CHECK (attempt_no > 0),
    CONSTRAINT ck_payment_transaction_status CHECK (status IN ('SUCCESS', 'FAILED', 'UNKNOWN'))
);

-- 결제 처리 이력 조회 인덱스
CREATE INDEX idx_payment_transaction_payment_id ON p_payment_transaction (payment_id);
CREATE INDEX idx_payment_transaction_status ON p_payment_transaction (status);
CREATE INDEX idx_payment_transaction_pg_transaction_id ON p_payment_transaction (pg_transaction_id);

-- 환불
CREATE TABLE p_refund (
    refund_id UUID NOT NULL,
    payment_id UUID NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    amount BIGINT NOT NULL,
    reason TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    pg_cancel_key VARCHAR(200),
    failure_code VARCHAR(100),
    failure_reason TEXT,
    requested_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_refund PRIMARY KEY (refund_id),
    CONSTRAINT fk_refund_payment FOREIGN KEY (payment_id) REFERENCES p_payment (payment_id) ON DELETE RESTRICT,
    CONSTRAINT uq_refund_payment_id UNIQUE (payment_id),
    CONSTRAINT uq_refund_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_refund_amount CHECK (amount >= 0),
    CONSTRAINT ck_refund_status CHECK (status IN ('REQUESTED', 'PROCESSING', 'REFUNDED', 'FAILED', 'UNKNOWN'))
);

-- 환불 조회 인덱스
CREATE INDEX idx_refund_payment_id ON p_refund (payment_id);
CREATE INDEX idx_refund_status ON p_refund (status);

-- 결제 이벤트 Outbox
CREATE TABLE p_payment_outbox (
    outbox_id UUID NOT NULL,
    payment_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_payment_outbox PRIMARY KEY (outbox_id),
    CONSTRAINT fk_payment_outbox_payment FOREIGN KEY (payment_id) REFERENCES p_payment (payment_id) ON DELETE RESTRICT,
    CONSTRAINT ck_payment_outbox_retry_count CHECK (retry_count >= 0),
    CONSTRAINT ck_payment_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

-- Outbox 조회 및 발행 대상 탐색 인덱스
CREATE INDEX idx_payment_outbox_payment_id ON p_payment_outbox (payment_id);
CREATE INDEX idx_payment_outbox_status ON p_payment_outbox (status);
CREATE INDEX idx_payment_outbox_pending ON p_payment_outbox (created_at) WHERE status = 'PENDING';

-- 사용자 알림
CREATE TABLE p_notification (
    notification_id UUID NOT NULL,
    source_event_id UUID NOT NULL,
    user_id BIGINT NOT NULL,
    reservation_id UUID NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    read_status VARCHAR(20) NOT NULL DEFAULT 'UNREAD',
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_notification PRIMARY KEY (notification_id),
    CONSTRAINT uq_notification_source_event UNIQUE (source_event_id),
    CONSTRAINT ck_notification_type CHECK (notification_type IN ('RESERVATION_CONFIRMED', 'RESERVATION_FAILED')),
    CONSTRAINT ck_notification_read_status CHECK (read_status IN ('UNREAD', 'READ')),
    CONSTRAINT ck_notification_read_consistency CHECK ((read_status = 'UNREAD' AND read_at IS NULL) OR (read_status = 'READ' AND read_at IS NOT NULL))
);

-- 알림 조회 인덱스
CREATE INDEX idx_notification_user_created_at ON p_notification (user_id, created_at DESC) WHERE is_deleted = FALSE;
CREATE INDEX idx_notification_user_unread ON p_notification (user_id, created_at DESC) WHERE is_deleted = FALSE AND read_status = 'UNREAD';
CREATE INDEX idx_notification_reservation ON p_notification (reservation_id) WHERE is_deleted = FALSE;
