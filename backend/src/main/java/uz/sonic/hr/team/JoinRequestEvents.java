package uz.sonic.hr.team;

public final class JoinRequestEvents {

    private JoinRequestEvents() {
    }

    public record TeamJoinRequested(Long requestId, Long teamId, String teamName, Long employeeId, String employeeName) {
    }

    public record TeamJoinApproved(Long requestId, Long teamId, Long employeeId, String employeeName, Long actorId) {
    }

    public record TeamJoinRejected(Long requestId, Long teamId, Long employeeId, String employeeName, Long actorId) {
    }
}
