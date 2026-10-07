package com.ticketmgmt.repository;

import com.ticketmgmt.model.Priority;
import com.ticketmgmt.model.Ticket;
import com.ticketmgmt.model.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Page<Ticket> findByStatus(TicketStatus status, Pageable pageable);

    Page<Ticket> findByCreatedById(Long createdById, Pageable pageable);

    Page<Ticket> findByAssigneeId(Long assigneeId, Pageable pageable);

    List<Ticket> findByStatusAndPriority(TicketStatus status, Priority priority);

    long countByStatus(TicketStatus status);
}
