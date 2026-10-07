package com.ticketmgmt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ResolveTicketRequest {

    @NotBlank(message = "Resolution notes are required")
    private String resolutionNotes;

    @NotNull(message = "Resolver id is required")
    private Long resolvedById;
}
