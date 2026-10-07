package com.ticketmgmt.service.aws;

import com.ticketmgmt.service.NotificationService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * AWS-backed {@link NotificationService} that delegates email to SES and
 * event publication to SNS. Active only under the {@code aws} profile.
 */
@Service
@Profile("aws")
public class AwsNotificationService implements NotificationService {

    private final SesEmailService sesEmailService;
    private final SnsEventPublisher snsEventPublisher;

    public AwsNotificationService(SesEmailService sesEmailService,
                                  SnsEventPublisher snsEventPublisher) {
        this.sesEmailService = sesEmailService;
        this.snsEventPublisher = snsEventPublisher;
    }

    @Override
    public void notifyTicketCreated(Long ticketId, String title, String priority,
                                    String createdBy, String managerEmail) {
        sesEmailService.sendTicketCreatedNotification(ticketId, title, priority, createdBy, managerEmail);
    }

    @Override
    public void notifyStatusChange(Long ticketId, String title, String newStatus,
                                   String recipientEmail) {
        sesEmailService.sendStatusChangeNotification(ticketId, title, newStatus, recipientEmail);
    }

    @Override
    public void notifyResolution(Long ticketId, String title, String recipientEmail,
                                 String pdfUrl) {
        sesEmailService.sendResolutionNotification(ticketId, title, recipientEmail, pdfUrl);
    }

    @Override
    public void publishEvent(Long ticketId, String title, String status, String priority,
                             String createdByEmail, String eventType) {
        snsEventPublisher.publishTicketEvent(ticketId, title, status, priority, createdByEmail, eventType);
    }
}
