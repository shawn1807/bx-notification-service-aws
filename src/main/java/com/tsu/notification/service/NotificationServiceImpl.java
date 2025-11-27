package com.tsu.notification.service;

import com.github.f4b6a3.uuid.UuidCreator;
import com.tsu.enums.DeliveryStatus;
import com.tsu.enums.ReadStatus;
import com.tsu.namespace.dto.NotificationRequest;
import com.tsu.notification.entities.NotificationRecipientTb;
import com.tsu.notification.entities.NotificationTb;
import com.tsu.notification.repo.NotificationRecipientRepository;
import com.tsu.notification.repo.NotificationRepository;
import com.tsu.notification.val.NotificationVal;
import com.tsu.notification.val.NotificationWithStateVal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepo;
    private final NotificationRecipientRepository recipientRepo;

    public NotificationServiceImpl(NotificationRepository notificationRepo,
                                   NotificationRecipientRepository recipientRepo) {
        this.notificationRepo = notificationRepo;
        this.recipientRepo = recipientRepo;
    }

    /**
     * Create a notification and fan-out to recipients.
     * Optionally send email and SMS using EmailService & SmsService.
     */
    @Transactional
    public NotificationVal notifyUsers(NotificationRequest request) {
        LocalDateTime now = LocalDateTime.now();
        NotificationTb notification = new NotificationTb();
        notification.setId(UuidCreator.getTimeOrderedEpoch());
        notification.setNamespaceId(request.namespaceId());
        notification.setType(request.type());
        notification.setTitle(request.title());
        notification.setBody(request.body());
        notification.setEntryId(request.entryId());
        notification.setSeverity(request.severity());
        notification.setBroadcast(false);
        notification.setCreatedBy(request.actorUserId());
        notification.setCreatedDate(now);
        NotificationTb saved = notificationRepo.save(notification);

        // After save, id.id should be set by JPA if using sequence identity.
        UUID notificationNumericId = saved.getId();

        // 2) Create recipient rows (in-app)
        List<NotificationRecipientTb> recipients = new ArrayList<>();

        for (UUID userId : request.recipients()) {
            NotificationRecipientTb rec = new NotificationRecipientTb();
            rec.setNotificationId(notificationNumericId);
            rec.setUserId(userId);
            rec.setDeliveryStatus(DeliveryStatus.pending);
            rec.setReadStatus(ReadStatus.unread);
            rec.setCreatedDate(now);
            recipients.add(rec);
        }
        recipientRepo.saveAll(recipients);
        return new NotificationVal(saved.getId(), saved.getNamespaceId(), saved.getType(), saved.getTitle(), saved.getBody(), saved.getEntryId(),
                saved.getSeverity(), saved.isBroadcast(), saved.getCreatedBy(), saved.getCreatedDate());
    }


    @Transactional(readOnly = true)
    public Page<NotificationWithStateVal> findUserNotifications(UUID userId, Pageable page) {
        return notificationRepo
                .findByUserIdOrderByCreatedDateDesc(userId, page);
    }

    @Transactional
    public void markAsRead(UUID notificationId,
                           UUID userId) {
        recipientRepo
                .findByNotificationIdAndUserId(notificationId, userId)
                .ifPresent(rec -> {
                    rec.setReadStatus(ReadStatus.read);
                    rec.setReadDate(LocalDateTime.now());
                    recipientRepo.save(rec);
                });
    }

    @Transactional
    public void markAllAsRead(UUID namespaceId, UUID userId) {
        var unread = recipientRepo
                .findByNamespaceIdAndUserIdAndReadStatus(namespaceId, userId, ReadStatus.unread);

        LocalDateTime now = LocalDateTime.now();
        for (NotificationRecipientTb rec : unread) {
            rec.setReadStatus(ReadStatus.read);
            rec.setReadDate(now);
        }
        recipientRepo.saveAll(unread);
    }
}

