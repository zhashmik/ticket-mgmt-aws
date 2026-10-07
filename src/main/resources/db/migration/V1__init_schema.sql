-- V1__init_schema.sql
-- IT Ticket Management System — Aurora MySQL

CREATE TABLE employees (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(150) NOT NULL UNIQUE,
    department  VARCHAR(100),
    project     VARCHAR(100),
    manager_id  BIGINT,
    cognito_sub VARCHAR(255) UNIQUE COMMENT 'Cognito User Pool subject ID',
    is_active   BOOLEAN DEFAULT TRUE,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_employees_email (email),
    INDEX idx_employees_manager (manager_id),
    INDEX idx_employees_cognito (cognito_sub),
    CONSTRAINT fk_employee_manager FOREIGN KEY (manager_id) REFERENCES employees(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE roles (
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO roles (name) VALUES ('USER'), ('MANAGER'), ('ADMIN');

CREATE TABLE employee_roles (
    employee_id BIGINT NOT NULL,
    role_id     BIGINT NOT NULL,
    PRIMARY KEY (employee_id, role_id),
    CONSTRAINT fk_er_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    CONSTRAINT fk_er_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tickets (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    title                VARCHAR(255) NOT NULL,
    description          TEXT,
    created_by_id        BIGINT NOT NULL,
    assignee_id          BIGINT,
    priority             ENUM('LOW','MEDIUM','HIGH','CRITICAL') DEFAULT 'MEDIUM',
    status               ENUM('OPEN','PENDING_APPROVAL','APPROVED','REJECTED',
                               'IN_PROGRESS','RESOLVED','CLOSED','REOPENED',
                               'AUTO_CLOSED') DEFAULT 'OPEN',
    category             VARCHAR(100),
    file_attachment_path VARCHAR(500) COMMENT 'S3 key',
    resolution_notes     TEXT,
    resolution_pdf_path  VARCHAR(500) COMMENT 'S3 key for PDF',
    creation_date        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated         TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    resolved_date        TIMESTAMP NULL,
    closed_date          TIMESTAMP NULL,
    INDEX idx_tickets_status (status),
    INDEX idx_tickets_priority (priority),
    INDEX idx_tickets_created_by (created_by_id),
    INDEX idx_tickets_assignee (assignee_id),
    INDEX idx_tickets_creation_date (creation_date),
    CONSTRAINT fk_ticket_creator FOREIGN KEY (created_by_id) REFERENCES employees(id),
    CONSTRAINT fk_ticket_assignee FOREIGN KEY (assignee_id) REFERENCES employees(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ticket_history (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id   BIGINT NOT NULL,
    action      VARCHAR(50) NOT NULL,
    action_by   VARCHAR(150) NOT NULL,
    action_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    comments    TEXT,
    INDEX idx_history_ticket (ticket_id),
    INDEX idx_history_action_date (action_date),
    CONSTRAINT fk_history_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notification_log (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id          BIGINT NOT NULL,
    recipient          VARCHAR(150) NOT NULL,
    notification_type  VARCHAR(50) NOT NULL,
    sent_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ses_message_id     VARCHAR(255),
    INDEX idx_notif_ticket (ticket_id),
    CONSTRAINT fk_notif_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
