package com.ticketmgmt.service.aws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.Map;

@Service
@Profile("aws")
public class SnsEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SnsEventPublisher.class);
    private final SnsClient snsClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sns.ticket-events-topic-arn}")
    private String topicArn;

    public SnsEventPublisher(SnsClient snsClient, ObjectMapper objectMapper) {
        this.snsClient = snsClient;
        this.objectMapper = objectMapper;
    }

    public void publishTicketEvent(Long ticketId, String title, String status,
            String priority, String createdByEmail, String eventType) {
        try {
            Map<String, Object> payload = Map.of(
                "eventType", eventType, "ticketId", ticketId,
                "title", title, "status", status,
                "priority", priority, "createdBy", createdByEmail,
                "timestamp", System.currentTimeMillis());

            snsClient.publish(PublishRequest.builder()
                .topicArn(topicArn)
                .message(objectMapper.writeValueAsString(payload))
                .messageAttributes(Map.of(
                    "eventType", MessageAttributeValue.builder()
                        .dataType("String").stringValue(eventType).build(),
                    "priority", MessageAttributeValue.builder()
                        .dataType("String").stringValue(priority).build()))
                .build());
            log.info("Published {} for ticket #{}", eventType, ticketId);
        } catch (Exception e) {
            log.error("SNS publish failed for ticket #{}: {}", ticketId, e.getMessage());
        }
    }
}
