package uz.sonic.hr.service;

import uz.sonic.hr.entity.TicketStatus;

public final class TicketEvents {

    private TicketEvents() {
    }

    /**
     * A new ticket was created by a user.
     */
    public record TicketCreated(Long ticketId, String subject, String creatorName, Long creatorId) {
    }

    /**
     * User added a message to their ticket.
     */
    public record TicketUserMessage(Long ticketId, String subject, String senderName) {
    }

    /**
     * Admin responded to a ticket.
     */
    public record TicketAdminResponse(Long ticketId, String subject, Long recipientId, String adminName) {
    }

    /**
     * Ticket status was changed by admin.
     */
    public record TicketStatusChanged(Long ticketId, String subject, Long creatorId, TicketStatus newStatus,
                                      String adminName) {
    }
}
