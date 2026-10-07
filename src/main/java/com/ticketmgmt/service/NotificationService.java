package com.ticketmgmt.service;

/**
 * Abstraction over outbound notifications (email + events) so the business
 * layer is decoupled from AWS. Backed by SES/SNS under the {@code aws} profile
 * and by logging under {@code local}.
 */
public interface NotificationService {

    void notifyTicketCreated(Long ticketId, String title, String priority,
                             String createdBy, String managerEmail);

    void notifyStatusChange(Long ticketId, String title, String newStatus,
                            String recipientEmail);

    void notifyResolution(Long ticketId, String title, String recipientEmail,
                          String pdfUrl);

    void publishEvent(Long ticketId, String title, String status, String priority,
                      String createdByEmail, String eventType);
}
