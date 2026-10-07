package com.ticketmgmt.controller;

import com.ticketmgmt.dto.CreateTicketRequest;
import com.ticketmgmt.dto.ResolveTicketRequest;
import com.ticketmgmt.dto.TicketResponse;
import com.ticketmgmt.dto.UpdateStatusRequest;
import com.ticketmgmt.model.TicketHistory;
import com.ticketmgmt.model.TicketStatus;
import com.ticketmgmt.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody CreateTicketRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.create(req));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ticketService.getById(id));
    }

    @GetMapping
    public ResponseEntity<Page<TicketResponse>> list(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "creationDate"));
        return ResponseEntity.ok(ticketService.list(status, pageable));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<TicketResponse> updateStatus(
            @PathVariable Long id, @Valid @RequestBody UpdateStatusRequest req) {
        return ResponseEntity.ok(ticketService.updateStatus(id, req));
    }

    @PostMapping("/resolve/{id}")
    public ResponseEntity<TicketResponse> resolve(
            @PathVariable Long id, @Valid @RequestBody ResolveTicketRequest req) {
        return ResponseEntity.ok(ticketService.resolve(id, req));
    }

    @PostMapping("/{id}/attachments")
    public ResponseEntity<TicketResponse> attach(
            @PathVariable Long id,
            @RequestParam Long actorId,
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(ticketService.attachFile(id, actorId, file));
    }

    @GetMapping("/{id}/attachments/url")
    public ResponseEntity<Map<String, String>> attachmentUrl(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("url", ticketService.getAttachmentUrl(id)));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<TicketHistory>> history(@PathVariable Long id) {
        return ResponseEntity.ok(ticketService.getHistory(id));
    }
}
