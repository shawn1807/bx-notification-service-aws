package com.tsu.notification.entities;

import com.tsu.enums.PushPlatform;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Device registration for push notifications
 */
@Entity
@Table(name = "device_push_token",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_device_platform", columnNames = {"device_id", "platform"})
    },
    indexes = {
        @Index(name = "idx_device_user_id", columnList = "user_id"),
        @Index(name = "idx_device_token", columnList = "token"),
        @Index(name = "idx_device_platform", columnList = "platform, active"),
        @Index(name = "idx_device_last_used", columnList = "last_used_at")
    })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DevicePushTokenTb {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 255)
    private UUID userId;

    @Column(name = "device_id", nullable = false, length = 255)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PushPlatform platform;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String token;

    @Column(name = "app_version", length = 50)
    private String appVersion;

    @Column(name = "os_version", length = 50)
    private String osVersion;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "last_used_at", nullable = false)
    @Builder.Default
    private Instant lastUsedAt = Instant.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void markAsUsed() {
        this.lastUsedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
        this.lastUsedAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.active = false;
    }
}
