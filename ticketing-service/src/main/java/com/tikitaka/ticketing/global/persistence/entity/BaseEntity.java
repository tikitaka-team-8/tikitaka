package com.tikitaka.ticketing.global.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class BaseEntity {

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, updatable = false)
    private Long createdBy;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private Long updatedBy;

    private Instant deletedAt;

    private Long deletedBy;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    protected BaseEntity() {
    }

    protected BaseEntity(Long createdBy) {
        Long userId = requireUserId(createdBy);
        this.createdBy = userId;
        this.updatedBy = userId;
    }

    protected final void markAsUpdated(Long updatedBy) {
        this.updatedBy = requireUserId(updatedBy);
    }

    protected final void markAsDeleted(Long deletedBy, Instant deletedAt) {
        if (deleted) {
            throw new IllegalStateException("이미 삭제된 데이터입니다.");
        }

        this.deletedBy = requireUserId(deletedBy);
        this.deletedAt = requireDeletedAt(deletedAt);
        this.deleted = true;
        this.updatedBy = this.deletedBy;
    }

    private static Long requireUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("감사 사용자 ID는 필수입니다.");
        }
        return userId;
    }

    private static Instant requireDeletedAt(Instant deletedAt) {
        if (deletedAt == null) {
            throw new IllegalArgumentException("삭제 시각은 필수입니다.");
        }
        return deletedAt;
    }

}
