package com.ticketmgmt.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "action_by", nullable = false, length = 150)
    private String actionBy;

    @Column(name = "action_date", updatable = false)
    private LocalDateTime actionDate;

    @Column(columnDefinition = "TEXT")
    private String comments;

    @PrePersist
    protected void onCreate() {
        this.actionDate = LocalDateTime.now();
    }
}
