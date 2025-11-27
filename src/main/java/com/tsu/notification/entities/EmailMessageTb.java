package com.tsu.notification.entities;

import com.tsu.enums.MessageStatus;
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
@Table(name = "email_message")
public class EmailMessageTb {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "namespace_id")
    private UUID namespaceId;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "to_email", nullable = false)
    private String toEmail;

    @Column(name = "cc_email")
    private String ccEmail;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "body")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MessageStatus status;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "expiry_date")
    private LocalDateTime expiryDate;

    @Column(name = "scheduled_date")
    private LocalDateTime scheduledDate;

    @Column(name = "sent_date")
    private LocalDateTime sentDate;

    @Column(name = "last_attempt_date")
    private LocalDateTime lastAttemptDate;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate;
}
