package uz.sonic.hr.employee;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.sonic.hr.employee.Employee;

import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByUsername(String username);

    Optional<Employee> findByTelegramChatId(Long telegramChatId);

    Optional<Employee> findByTelegramLinkCode(String telegramLinkCode);

    boolean existsByUsername(String username);
}
