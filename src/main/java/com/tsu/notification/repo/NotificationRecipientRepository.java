package com.tsu.notification.repo;

import com.tsu.enums.ReadStatus;
import com.tsu.notification.entities.NotificationRecipientTb;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Repository
public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipientTb, UUID> {

    Stream<NotificationRecipientTb> findByUserIdOrderByCreatedDateDesc(
            UUID userId
    );

    List<NotificationRecipientTb> findByNamespaceIdAndUserIdAndReadStatus(
            UUID namespaceId, UUID userId, ReadStatus readStatus
    );

    Optional<NotificationRecipientTb> findByNotificationIdAndUserId(
            UUID notificationId, UUID userId
    );

    List<NotificationRecipientTb> findByNamespaceIdAndNotificationId(
            UUID namespaceId, UUID notificationId
    );
}
