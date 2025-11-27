package com.tsu.notification.entities;

import com.tsu.enums.AppSeverity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@ToString
@EqualsAndHashCode
@Entity
@Table(name = "notification")
public class NotificationTb {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "namespace_id")
    private UUID namespaceId;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "title")
    private String title;

    @Column(name = "body")
    private String body;

    @Column(name = "entry_id")
    private UUID entryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity")
    private AppSeverity severity;

    @Column(name = "is_broadcast")
    private boolean isBroadcast;

    @Column(name = "schedule_date", nullable = false)
    private LocalDateTime scheduleDate;

    @Column(name = "expired_date", nullable = false)
    private LocalDateTime expiredDate;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate;
}
