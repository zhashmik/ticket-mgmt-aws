package com.ticketmgmt.service.aws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

@Service
@Profile("aws")
public class SesEmailService {

    private static final Logger log = LoggerFactory.getLogger(SesEmailService.class);
    private final SesClient sesClient;

    @Value("${aws.ses.from-email}")
    private String fromEmail;

    public SesEmailService(SesClient sesClient) {
        this.sesClient = sesClient;
    }

    public void sendTicketCreatedNotification(Long ticketId, String title,
            String priority, String createdBy, String managerEmail) {
        String subject = String.format("[Ticket #%d] New: %s", ticketId, title);
        String body = String.format("""
            <html><body style="font-family:Arial;padding:20px;">
            <h2 style="color:#232F3E;">New Ticket Requires Approval</h2>
            <table style="border-collapse:collapse;width:100%%;">
            <tr><td style="padding:8px;font-weight:bold;">ID:</td><td>#%d</td></tr>
            <tr><td style="padding:8px;font-weight:bold;">Title:</td><td>%s</td></tr>
            <tr><td style="padding:8px;font-weight:bold;">Priority:</td><td>%s</td></tr>
            <tr><td style="padding:8px;font-weight:bold;">Created By:</td><td>%s</td></tr>
            </table>
            <p>Please review and take action.</p>
            </body></html>""", ticketId, title, priority, createdBy);
        sendEmail(managerEmail, subject, body);
    }

    public void sendStatusChangeNotification(Long ticketId, String title,
            String newStatus, String recipientEmail) {
        String subject = String.format("[Ticket #%d] Status: %s", ticketId, newStatus);
        String body = String.format("""
            <html><body style="font-family:Arial;padding:20px;">
            <h2 style="color:#232F3E;">Ticket Status Update</h2>
            <p>Ticket <strong>#%d — %s</strong> is now <strong style="color:#FF9900;">%s</strong></p>
            </body></html>""", ticketId, title, newStatus);
        sendEmail(recipientEmail, subject, body);
    }

    public void sendResolutionNotification(Long ticketId, String title,
            String recipientEmail, String pdfUrl) {
        String subject = String.format("[Ticket #%d] Resolved — PDF Available", ticketId);
        String body = String.format("""
            <html><body style="font-family:Arial;padding:20px;">
            <h2 style="color:#232F3E;">Ticket Resolved</h2>
            <p>Ticket <strong>#%d — %s</strong> has been resolved.</p>
            <p><a href="%s" style="background:#FF9900;color:white;padding:10px 20px;
            text-decoration:none;border-radius:5px;">Download PDF</a></p>
            <p style="color:#999;">Link expires in 15 minutes.</p>
            </body></html>""", ticketId, title, pdfUrl);
        sendEmail(recipientEmail, subject, body);
    }

    public void sendPendingReminder(Long ticketId, String title,
            String priority, String managerEmail) {
        String subject = String.format("[REMINDER] Ticket #%d Pending", ticketId);
        String body = String.format("""
            <html><body style="font-family:Arial;padding:20px;">
            <h2 style="color:#D13212;">Pending Ticket Reminder</h2>
            <p>Ticket <strong>#%d — %s</strong> (Priority: %s) needs action.</p>
            </body></html>""", ticketId, title, priority);
        sendEmail(managerEmail, subject, body);
    }

    private void sendEmail(String to, String subject, String htmlBody) {
        try {
            sesClient.sendEmail(SendEmailRequest.builder()
                .source(fromEmail)
                .destination(Destination.builder().toAddresses(to).build())
                .message(Message.builder()
                    .subject(Content.builder().data(subject).charset("UTF-8").build())
                    .body(Body.builder()
                        .html(Content.builder().data(htmlBody).charset("UTF-8").build())
                        .build()).build()).build());
            log.info("Email sent to {} — {}", to, subject);
        } catch (SesException e) {
            log.error("Email failed to {}: {}", to, e.awsErrorDetails().errorMessage());
        }
    }
}
