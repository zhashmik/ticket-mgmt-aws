package com.ticketmgmt.dto;

import com.ticketmgmt.model.Priority;
import com.ticketmgmt.model.Ticket;
import com.ticketmgmt.model.TicketStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TicketResponse {

    private Long id;
    private String title;
    private String description;
    private Long createdById;
    private Long assigneeId;
    private Priority priority;
    private TicketStatus status;
    private String category;
    private String fileAttachmentPath;
    private String resolutionNotes;
    private String resolutionPdfPath;
    private LocalDateTime creationDate;
    private LocalDateTime lastUpdated;
    private LocalDateTime resolvedDate;
    private LocalDateTime closedDate;

    public static TicketResponse from(Ticket t) {
        return TicketResponse.builder()
                .id(t.getId())
                .title(t.getTitle())
                .description(t.getDescription())
                .createdById(t.getCreatedById())
                .assigneeId(t.getAssigneeId())
                .priority(t.getPriority())
                .status(t.getStatus())
                .category(t.getCategory())
                .fileAttachmentPath(t.getFileAttachmentPath())
                .resolutionNotes(t.getResolutionNotes())
                .resolutionPdfPath(t.getResolutionPdfPath())
                .creationDate(t.getCreationDate())
                .lastUpdated(t.getLastUpdated())
                .resolvedDate(t.getResolvedDate())
                .closedDate(t.getClosedDate())
                .build();
    }
}
