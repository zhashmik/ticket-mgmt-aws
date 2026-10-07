package com.ticketmgmt.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

import java.sql.*;

public class PendingTicketReminderLambda implements RequestHandler<Object, String> {

    private static final SesClient ses = SesClient.create();
    private static final String DB_ENDPOINT = System.getenv("DB_ENDPOINT");
    private static final String DB_USER = System.getenv("DB_USERNAME");
    private static final String DB_PASS = System.getenv("DB_PASSWORD");
    private static final String FROM_EMAIL = System.getenv("SES_FROM_EMAIL");

    @Override
    public String handleRequest(Object event, Context ctx) {
        int sent = 0, closed = 0;
        try (Connection conn = DriverManager.getConnection(
                "jdbc:mysql://" + DB_ENDPOINT + ":3306/ticket_mgmt", DB_USER, DB_PASS)) {
            // Send reminders for tickets pending > 2 days
            String q = "SELECT t.id,t.title,t.priority,e.email FROM tickets t " +
                       "JOIN employees e ON t.assignee_id=e.id " +
                       "WHERE t.status='PENDING_APPROVAL' AND t.creation_date<DATE_SUB(NOW(),INTERVAL 2 DAY)";
            try (ResultSet rs = conn.prepareStatement(q).executeQuery()) {
                while (rs.next()) {
                    sendReminder(rs.getString("email"), rs.getLong("id"),
                        rs.getString("title"), rs.getString("priority"));
                    sent++;
                }
            }
            // Auto-close stale tickets > 30 days
            closed = conn.prepareStatement(
                "UPDATE tickets SET status='AUTO_CLOSED',last_updated=NOW() " +
                "WHERE status IN('PENDING_APPROVAL','OPEN') " +
                "AND last_updated<DATE_SUB(NOW(),INTERVAL 30 DAY)").executeUpdate();
        } catch (Exception e) {
            ctx.getLogger().log("ERROR: " + e.getMessage());
            throw new RuntimeException(e);
        }
        String result = String.format("Reminders:%d, AutoClosed:%d", sent, closed);
        ctx.getLogger().log(result);
        return result;
    }

    private void sendReminder(String to, long id, String title, String priority) {
        ses.sendEmail(SendEmailRequest.builder().source(FROM_EMAIL)
            .destination(Destination.builder().toAddresses(to).build())
            .message(Message.builder()
                .subject(Content.builder().data(String.format("[REMINDER] Ticket #%d Pending", id)).build())
                .body(Body.builder().html(Content.builder().data(String.format(
                    "<html><body><h2>Pending Reminder</h2><p>#%d — %s (Priority: %s)</p></body></html>",
                    id, title, priority)).build()).build()).build()).build());
    }
}
