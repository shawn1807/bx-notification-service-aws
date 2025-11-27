package com.tsu.notification.repo;

import com.tsu.notification.val.NotificationWithStateVal;
import com.tsu.notification.entities.NotificationTb;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationTb, UUID> {


    @Query(value = """
            SELECT 
            n.id as notification_id,
            n.namespace_id,
            n.type,
            n.body,
            n.entry_id,
            e.custom_type as entry_type,
            n.created_by,
            n.created_date,
            rec.read_status,
            rec.delivery_status,
            rec.delivered_date,
            rec.read_date
            FROM notification n join notification_recipient rec on n.id = rec.notification_id
            LEFT JOIN entry e ON n.entry_id = e.id
            WHERE rec.user_id = :userId
            ORDER BY n.created_date desc
            """,countQuery = """
            SELECT COUNT(*)
            FROM notification n join notification_recipient rec on n.id = rec.notification_id
            WHERE rec.user_id = :userId
            """, nativeQuery = true)
    Page<NotificationWithStateVal> findByUserIdOrderByCreatedDateDesc(@Param("userId") UUID userId, Pageable pageable);
}
