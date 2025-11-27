package com.tsu.notification.service;


import com.github.f4b6a3.uuid.UuidCreator;
import com.tsu.enums.MessageChannel;
import com.tsu.enums.MessageStatus;
import com.tsu.notification.val.SmsMessageVal;
import com.tsu.notification.entities.SmsMessageTb;
import com.tsu.notification.repo.SmsMessageRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class SmsServiceImpl implements SmsService {


    private final SmsMessageRepository smsRepo;
    private final OutboxPublisher outboxPublisher;

    public SmsServiceImpl(
            SmsMessageRepository smsRepo,
            OutboxPublisher outboxPublisher) {
        this.smsRepo = smsRepo;
        this.outboxPublisher = outboxPublisher;
    }


    @Transactional
    @Override
    public SmsMessageVal queueSms(UUID namespaceId,Enum<?> type, String phoneE164, String messageBody, UUID createdBy, LocalDateTime scheduledAt) {
        SmsMessageTb msg = new SmsMessageTb();
        msg.setId(UuidCreator.getTimeOrderedEpoch());
        msg.setNamespaceId(namespaceId);
        msg.setCreatedBy(createdBy);
        msg.setPhoneNumber(phoneE164);
        msg.setType(String.valueOf(type));
        msg.setBody(messageBody);
        msg.setStatus(MessageStatus.queued);
        msg.setScheduledDate(scheduledAt != null ? scheduledAt : LocalDateTime.now());
        msg.setCreatedDate(LocalDateTime.now());
        SmsMessageTb sms = smsRepo.save(msg);
        outboxPublisher.publish(MessageChannel.email, msg.getId(), type);
        return new SmsMessageVal(sms.getId(), sms.getNamespaceId(), sms.getPhoneNumber(), sms.getBody(), sms.getStatus(),
                sms.getLastError(), sms.getExpiryDate(), sms.getScheduledDate(), sms.getSentDate(), sms.getLastAttemptDate(), sms.getAttempts(), sms.getCreatedBy(), sms.getCreatedDate());
    }


}
