package com.ticketmgmt.service.local;

import com.ticketmgmt.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Development implementation of {@link NotificationService} that logs instead
 * of calling SES/SNS. Active only under the {@code local} profile.
 */
@Service
@Profile("local")
public class LocalNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(LocalNotificationService.class);

    @Override
    public void notifyTicketCreated(Long ticketId, String title, String priority,
                                    String createdBy, String managerEmail) {
        log.info("[EMAIL] ticket #{} created '{}' (priority {}) by {} -> notify {}",
                ticketId, title, priority, createdBy, managerEmail);
    }

    @Override
    public void notifyStatusChange(Long ticketId, String title, String newStatus,
                                   String recipientEmail) {
        log.info("[EMAIL] ticket #{} '{}' status -> {} -> notify {}",
                ticketId, title, newStatus, recipientEmail);
    }

    @Override
    public void notifyResolution(Long ticketId, String title, String recipientEmail,
                                 String pdfUrl) {
        log.info("[EMAIL] ticket #{} '{}' resolved -> notify {} (pdf: {})",
                ticketId, title, recipientEmail, pdfUrl);
    }

    @Override
    public void publishEvent(Long ticketId, String title, String status, String priority,
                             String createdByEmail, String eventType) {
        log.info("[EVENT] {} ticket #{} '{}' status={} priority={} by={}",
                eventType, ticketId, title, status, priority, createdByEmail);
    }
}
