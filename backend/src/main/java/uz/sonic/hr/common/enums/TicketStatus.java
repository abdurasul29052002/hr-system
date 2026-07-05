package uz.sonic.hr.common.enums;

public enum TicketStatus {
    OPEN,         // New ticket, not yet assigned
    IN_PROGRESS,  // Admin is working on it
    RESOLVED,     // Issue resolved, waiting for confirmation
    CLOSED        // Ticket closed (resolved or invalid)
}
