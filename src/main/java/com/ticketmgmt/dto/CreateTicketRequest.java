package com.ticketmgmt.dto;

import com.ticketmgmt.model.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateTicketRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must be at most 255 characters")
    private String title;

    @Size(max = 5000, message = "Description must be at most 5000 characters")
    private String description;

    @NotNull(message = "Priority is required")
    private Priority priority;

    @Size(max = 100)
    private String category;

    @NotNull(message = "Creator id is required")
    private Long createdById;

    private Long assigneeId;
}
