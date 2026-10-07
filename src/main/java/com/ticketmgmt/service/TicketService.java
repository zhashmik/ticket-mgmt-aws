package com.ticketmgmt.service;

import com.ticketmgmt.dto.CreateTicketRequest;
import com.ticketmgmt.dto.ResolveTicketRequest;
import com.ticketmgmt.dto.TicketResponse;
import com.ticketmgmt.dto.UpdateStatusRequest;
import com.ticketmgmt.exception.ResourceNotFoundException;
import com.ticketmgmt.model.Employee;
import com.ticketmgmt.model.Ticket;
import com.ticketmgmt.model.TicketHistory;
import com.ticketmgmt.model.TicketStatus;
import com.ticketmgmt.repository.EmployeeRepository;
import com.ticketmgmt.repository.TicketHistoryRepository;
import com.ticketmgmt.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private final TicketRepository ticketRepository;
    private final TicketHistoryRepository historyRepository;
    private final EmployeeRepository employeeRepository;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;

    public TicketService(TicketRepository ticketRepository,
                         TicketHistoryRepository historyRepository,
                         EmployeeRepository employeeRepository,
                         FileStorageService fileStorageService,
                         NotificationService notificationService) {
        this.ticketRepository = ticketRepository;
        this.historyRepository = historyRepository;
        this.employeeRepository = employeeRepository;
        this.fileStorageService = fileStorageService;
        this.notificationService = notificationService;
    }

    @Transactional
    public TicketResponse create(CreateTicketRequest req) {
        Employee creator = employeeRepository.findById(req.getCreatedById())
                .orElseThrow(() -> new ResourceNotFoundException("Employee", req.getCreatedById()));

        Ticket ticket = Ticket.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .priority(req.getPriority())
                .category(req.getCategory())
                .createdById(req.getCreatedById())
                .assigneeId(req.getAssigneeId())
                .status(TicketStatus.PENDING_APPROVAL)
                .build();
        ticket = ticketRepository.save(ticket);

        record(ticket.getId(), "CREATED", creator.getEmail(), "Ticket created");

        String managerEmail = resolveManagerEmail(creator);
        notificationService.notifyTicketCreated(ticket.getId(), ticket.getTitle(),
                ticket.getPriority().name(), creator.getEmail(), managerEmail);
        notificationService.publishEvent(ticket.getId(), ticket.getTitle(),
                ticket.getStatus().name(), ticket.getPriority().name(),
                creator.getEmail(), "TICKET_CREATED");

        log.info("Created ticket #{} by {}", ticket.getId(), creator.getEmail());
        return TicketResponse.from(ticket);
    }

    @Transactional(readOnly = true)
    public TicketResponse getById(Long id) {
        return TicketResponse.from(findTicket(id));
    }

    @Transactional(readOnly = true)
    public Page<TicketResponse> list(TicketStatus status, Pageable pageable) {
        Page<Ticket> page = (status == null)
                ? ticketRepository.findAll(pageable)
                : ticketRepository.findByStatus(status, pageable);
        return page.map(TicketResponse::from);
    }

    @Transactional
    public TicketResponse updateStatus(Long id, UpdateStatusRequest req) {
        Ticket ticket = findTicket(id);
        Employee actor = employeeRepository.findById(req.getActedById())
                .orElseThrow(() -> new ResourceNotFoundException("Employee", req.getActedById()));

        TicketStatus previous = ticket.getStatus();
        ticket.setStatus(req.getStatus());
        if (req.getStatus() == TicketStatus.CLOSED || req.getStatus() == TicketStatus.AUTO_CLOSED) {
            ticket.setClosedDate(LocalDateTime.now());
        }
        ticketRepository.save(ticket);

        record(id, "STATUS_CHANGE", actor.getEmail(),
                String.format("%s -> %s. %s", previous, req.getStatus(),
                        req.getComments() == null ? "" : req.getComments()));

        Employee creator = employeeRepository.findById(ticket.getCreatedById()).orElse(null);
        if (creator != null) {
            notificationService.notifyStatusChange(id, ticket.getTitle(),
                    req.getStatus().name(), creator.getEmail());
        }
        notificationService.publishEvent(id, ticket.getTitle(), ticket.getStatus().name(),
                ticket.getPriority().name(),
                creator != null ? creator.getEmail() : "unknown", "TICKET_STATUS_CHANGED");

        return TicketResponse.from(ticket);
    }

    @Transactional
    public TicketResponse resolve(Long id, ResolveTicketRequest req) {
        Ticket ticket = findTicket(id);
        Employee resolver = employeeRepository.findById(req.getResolvedById())
                .orElseThrow(() -> new ResourceNotFoundException("Employee", req.getResolvedById()));

        ticket.setResolutionNotes(req.getResolutionNotes());
        ticket.setStatus(TicketStatus.RESOLVED);
        ticket.setResolvedDate(LocalDateTime.now());
        ticketRepository.save(ticket);

        record(id, "RESOLVED", resolver.getEmail(), req.getResolutionNotes());

        Employee creator = employeeRepository.findById(ticket.getCreatedById()).orElse(null);
        String recipient = creator != null ? creator.getEmail() : resolver.getEmail();
        notificationService.publishEvent(id, ticket.getTitle(), ticket.getStatus().name(),
                ticket.getPriority().name(), recipient, "TICKET_RESOLVED");

        if (ticket.getResolutionPdfPath() != null) {
            String url = fileStorageService.generateDownloadUrl(ticket.getResolutionPdfPath());
            notificationService.notifyResolution(id, ticket.getTitle(), recipient, url);
        }

        log.info("Resolved ticket #{} by {}", id, resolver.getEmail());
        return TicketResponse.from(ticket);
    }

    @Transactional
    public TicketResponse attachFile(Long id, Long actorId, MultipartFile file) throws IOException {
        Ticket ticket = findTicket(id);
        Employee actor = employeeRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", actorId));

        String key = fileStorageService.uploadAttachment(id, file);
        ticket.setFileAttachmentPath(key);
        ticketRepository.save(ticket);

        record(id, "ATTACHMENT", actor.getEmail(), "Attached " + file.getOriginalFilename());
        return TicketResponse.from(ticket);
    }

    @Transactional(readOnly = true)
    public String getAttachmentUrl(Long id) {
        Ticket ticket = findTicket(id);
        if (ticket.getFileAttachmentPath() == null) {
            throw new ResourceNotFoundException("Attachment for ticket", id);
        }
        return fileStorageService.generateDownloadUrl(ticket.getFileAttachmentPath());
    }

    @Transactional(readOnly = true)
    public List<TicketHistory> getHistory(Long id) {
        findTicket(id);
        return historyRepository.findByTicketIdOrderByActionDateDesc(id);
    }

    private Ticket findTicket(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", id));
    }

    private void record(Long ticketId, String action, String by, String comments) {
        historyRepository.save(TicketHistory.builder()
                .ticketId(ticketId)
                .action(action)
                .actionBy(by)
                .comments(comments)
                .build());
    }

    private String resolveManagerEmail(Employee creator) {
        if (creator.getManagerId() == null) {
            return creator.getEmail();
        }
        return employeeRepository.findById(creator.getManagerId())
                .map(Employee::getEmail)
                .orElse(creator.getEmail());
    }
}
