package com.ticketmgmt.dto;

import com.ticketmgmt.model.TicketStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateStatusRequest {

    @NotNull(message = "Status is required")
    private TicketStatus status;

    @NotNull(message = "Acting user id is required")
    private Long actedById;

    private String comments;
}
