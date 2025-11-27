package com.tsu.notification.repo;

import com.tsu.notification.entities.SmsMessageTb;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SmsMessageRepository extends JpaRepository<SmsMessageTb, UUID> {
}
