package com.ticketmgmt.controller;

import com.ticketmgmt.model.TicketStatus;
import com.ticketmgmt.repository.TicketRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final TicketRepository ticketRepository;

    public DashboardController(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (TicketStatus status : TicketStatus.values()) {
            byStatus.put(status.name(), ticketRepository.countByStatus(status));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", ticketRepository.count());
        result.put("byStatus", byStatus);
        return result;
    }
}
