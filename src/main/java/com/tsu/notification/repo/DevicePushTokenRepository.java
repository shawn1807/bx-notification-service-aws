package com.tsu.notification.repo;

import com.tsu.notification.entities.DevicePushToken;
import com.tsu.enums.PushPlatform;
import com.tsu.notification.entities.DevicePushTokenTb;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Repository
public interface DevicePushTokenRepository extends JpaRepository<DevicePushTokenTb, UUID> {

    /**
     * Find active tokens for a user
     */
    Stream<DevicePushTokenTb> findByUserIdAndActiveAndDeletedAtIsNull(UUID userId, Boolean active);

    /**
     * Find active tokens for a user by platform
     */
    Stream<DevicePushTokenTb> findByUserIdAndPlatformAndActiveAndDeletedAtIsNull(
            UUID userId,
            PushPlatform platform,
            Boolean active
    );

    /**
     * Find token by device and platform
     */
    Optional<DevicePushTokenTb> findByDeviceIdAndPlatform(String deviceId, PushPlatform platform);

    /**
     * Find token by token value
     */
    Optional<DevicePushTokenTb> findByTokenAndActiveAndDeletedAtIsNull(String token, Boolean active);

    /**
     * Deactivate old tokens for a device (when registering new token)
     */
    @Modifying
    @Query("UPDATE DevicePushToken d SET d.active = false WHERE d.deviceId = :deviceId AND d.platform = :platform AND d.id != :currentId")
    void deactivateOldTokens(
            @Param("deviceId") String deviceId,
            @Param("platform") PushPlatform platform,
            @Param("currentId") UUID currentId
    );

    /**
     * Find stale tokens (not used in X days)
     */
    @Query("SELECT d FROM DevicePushToken d WHERE d.lastUsedAt < :threshold AND d.active = true AND d.deletedAt IS NULL")
    Stream<DevicePushTokenTb> findStaleTokens(@Param("threshold") Instant threshold);

    /**
     * Count active tokens for a user
     */
    long countByUserIdAndActiveAndDeletedAtIsNull(UUID userId, Boolean active);
}
