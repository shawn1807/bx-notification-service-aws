package com.tsu.notification.service;

import com.github.f4b6a3.uuid.UuidCreator;
import com.tsu.auth.security.AppSettings;
import com.tsu.enums.MessageChannel;
import com.tsu.enums.MessageStatus;
import com.tsu.notification.entities.EmailMessageTb;
import com.tsu.notification.repo.EmailMessageRepository;
import com.tsu.notification.val.EmailMessageVal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class EmailServiceImpl implements EmailService {

    private final EmailMessageRepository repository;
    private final AppSettings settings;
    private final OutboxPublisher outboxPublisher;

    public EmailServiceImpl(EmailMessageRepository emailRepo,
                            OutboxPublisher outboxPublisher,
                            AppSettings settings) {
        this.repository = emailRepo;
        this.outboxPublisher = outboxPublisher;
        this.settings = settings;
    }

    @Transactional
    public EmailMessageVal queueEmail(
            UUID namespaceId,
            Enum<?> type,
            String to,
            String subject,
            String body,
            UUID createdBy,
            LocalDateTime scheduledAt
    ) {
        EmailMessageTb entity = new EmailMessageTb();
        entity.setId(UuidCreator.getTimeOrderedEpoch());
        entity.setNamespaceId(namespaceId);
        entity.setCreatedBy(createdBy);
        entity.setToEmail(to);
        entity.setSubject(subject);
        entity.setBody(body);
        entity.setStatus(MessageStatus.queued);
        entity.setScheduledDate(scheduledAt != null ? scheduledAt : LocalDateTime.now());
        entity.setCreatedDate(LocalDateTime.now());
        entity.setExpiryDate(LocalDateTime.now().plusMinutes(settings.getEmailDeliveryExpiryMinutes()));
        EmailMessageTb email = repository.save(entity);
        outboxPublisher.publish(MessageChannel.email, entity.getId(), type);
        return new EmailMessageVal(email.getId(), email.getNamespaceId(), email.getToEmail(), email.getCcEmail(), email.getSubject(), email.getBody(), email.getStatus(),
                email.getLastError(), email.getExpiryDate(), email.getScheduledDate(), email.getSentDate(), email.getLastAttemptDate(), email.getAttempts(), email.getCreatedDate());
    }


}

