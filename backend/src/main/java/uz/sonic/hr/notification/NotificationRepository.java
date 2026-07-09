package uz.sonic.hr.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import uz.sonic.hr.notification.Notification;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findAllByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    List<Notification> findAllByEmployeeIdAndIsReadOrderByCreatedAtDesc(Long employeeId, boolean isRead);

    long countByEmployeeIdAndIsRead(Long employeeId, boolean isRead);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.employee.id = :employeeId AND n.isRead = false")
    void markAllAsReadByEmployeeId(Long employeeId);

    /** Removes every notification addressed to the given recipient (used on account deletion). */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.employee.id = :employeeId")
    void deleteByEmployeeId(Long employeeId);
}
