package com.tsu.notification.service;

import com.tsu.audit.api.AuditLogger;
import com.tsu.audit.data.SystemAuditLog;
import com.tsu.auth.permissions.SystemAction;
import com.tsu.auth.security.AppSecurityContext;
import com.tsu.enums.AppSeverity;
import com.tsu.enums.PushPlatform;
import com.tsu.namespace.api.AppUser;
import com.tsu.notification.dto.RegisterDevice;
import com.tsu.notification.entities.DevicePushTokenTb;
import com.tsu.notification.repo.DevicePushTokenRepository;
import com.tsu.notification.val.DevicePushTokenVal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.stream.Stream;

/**
 * Service for managing device push tokens
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceServiceImpl implements DeviceService {

    private final DevicePushTokenRepository deviceRepository;
    private final AuditLogger auditLogger;


    @Transactional
    @Override
    public DevicePushTokenVal registerDevice(RegisterDevice request, AppSecurityContext context) {
        UUID userId = context.getPrincipal().id();
        log.info("Registering device: userId={}, deviceId={}, platform={}",
                userId, request.getDeviceId(), request.getPlatform());
        auditLogger.log(SystemAuditLog.builder()
                .severity(AppSeverity.I)
                .action(SystemAction.REGISTER_DEVICE)
                .subjectType(AppUser.class)
                .subjectId(context.getPrincipal().id())
                .txid(context.getTxid())
                .requestId(context.getRequestId())
                .meta(request)
                .actorId(userId)
                .build());
        DevicePushTokenTb device = deviceRepository.findByDeviceIdAndPlatform(
                request.getDeviceId(),
                request.getPlatform()
        ).orElseGet(() -> DevicePushTokenTb.builder()
                .userId(userId)
                .deviceId(request.getDeviceId())
                .platform(request.getPlatform())
                .token(request.getToken())
                .appVersion(request.getAppVersion())
                .osVersion(request.getOsVersion())
                .active(true)
                .build());
        device.setToken(request.getToken());
        device.setAppVersion(request.getAppVersion());
        device.setOsVersion(request.getOsVersion());
        device.activate();
        deviceRepository.save(device);
        // Deactivate other tokens for this device
        deviceRepository.deactivateOldTokens(request.getDeviceId(), request.getPlatform(), device.getId());
        return toDevicePushTokenVal(device);
    }

    @Override
    public Stream<DevicePushTokenVal> findActiveTokensByUser(UUID userId) {
        return deviceRepository.findByUserIdAndActiveAndDeletedAtIsNull(userId, true)
                .map(DeviceServiceImpl::toDevicePushTokenVal);
    }

    @Transactional(readOnly = true)
    @Override
    public Stream<DevicePushTokenVal> findActiveTokensByPlatform(UUID userId, PushPlatform platform) {
        return deviceRepository.findByUserIdAndPlatformAndActiveAndDeletedAtIsNull(userId, platform, true)
                .map(DeviceServiceImpl::toDevicePushTokenVal);
    }

    private static DevicePushTokenVal toDevicePushTokenVal(DevicePushTokenTb tb) {
        return new DevicePushTokenVal(tb.getId(), tb.getDeviceId(), tb.getToken(), tb.getAppVersion(), tb.getOsVersion());
    }

    /**
     * Deactivate a device token
     */
    @Transactional
    public void deactivateDevice(UUID userId, UUID deviceId, AppSecurityContext context) {
        deviceRepository.findById(deviceId).ifPresent(device -> {
            device.deactivate();
            deviceRepository.save(device);
            log.info("Deactivated device: id={}", deviceId);
        });
    }
}
