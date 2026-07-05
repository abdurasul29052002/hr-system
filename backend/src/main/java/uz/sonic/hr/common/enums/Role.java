package uz.sonic.hr.common.enums;

/** Per-team role, held on TeamMembership. The project owner is Employee.admin instead. */
public enum Role {
    /** Full team management including role changes. */
    LEADER,
    /** Manages members: creates and assigns tasks, cannot change roles. */
    MANAGER,
    /** Regular team member: takes and completes tasks. */
    MEMBER
}
