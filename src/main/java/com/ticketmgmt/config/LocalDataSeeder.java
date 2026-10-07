package com.ticketmgmt.config;

import com.ticketmgmt.model.Employee;
import com.ticketmgmt.model.Priority;
import com.ticketmgmt.model.Ticket;
import com.ticketmgmt.model.TicketStatus;
import com.ticketmgmt.repository.EmployeeRepository;
import com.ticketmgmt.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Seeds a small dataset for local development so the UI and API have content.
 * Runs only under the {@code local} profile and only when the DB is empty.
 */
@Component
@Profile("local")
public class LocalDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalDataSeeder.class);

    private final EmployeeRepository employeeRepository;
    private final TicketRepository ticketRepository;

    public LocalDataSeeder(EmployeeRepository employeeRepository, TicketRepository ticketRepository) {
        this.employeeRepository = employeeRepository;
        this.ticketRepository = ticketRepository;
    }

    @Override
    public void run(String... args) {
        if (employeeRepository.count() > 0) {
            return;
        }
        log.info("Seeding local development data...");

        Employee manager = employeeRepository.save(Employee.builder()
                .name("Dana Manager").email("dana.manager@ticketmgmt.local")
                .department("IT").project("Platform").isActive(true).build());

        Employee alice = employeeRepository.save(Employee.builder()
                .name("Alice User").email("alice.user@ticketmgmt.local")
                .department("Engineering").project("Platform")
                .managerId(manager.getId()).isActive(true).build());

        Employee bob = employeeRepository.save(Employee.builder()
                .name("Bob Agent").email("bob.agent@ticketmgmt.local")
                .department("IT Support").project("Helpdesk")
                .managerId(manager.getId()).isActive(true).build());

        ticketRepository.save(Ticket.builder()
                .title("Laptop will not boot")
                .description("Dell XPS shows black screen after update.")
                .priority(Priority.HIGH).status(TicketStatus.PENDING_APPROVAL)
                .category("Hardware").createdById(alice.getId()).assigneeId(bob.getId())
                .build());

        ticketRepository.save(Ticket.builder()
                .title("VPN keeps disconnecting")
                .description("VPN drops every few minutes on the corporate network.")
                .priority(Priority.MEDIUM).status(TicketStatus.IN_PROGRESS)
                .category("Network").createdById(alice.getId()).assigneeId(bob.getId())
                .build());

        ticketRepository.save(Ticket.builder()
                .title("Request new monitor")
                .description("Need a second monitor for the dev workstation.")
                .priority(Priority.LOW).status(TicketStatus.OPEN)
                .category("Equipment").createdById(bob.getId())
                .build());

        log.info("Seeded {} employees and {} tickets",
                employeeRepository.count(), ticketRepository.count());
    }
}
