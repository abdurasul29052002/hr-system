package uz.sonic.hr.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.sonic.hr.entity.Employee;

import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByUsername(String username);

    Optional<Employee> findByTelegramChatId(Long telegramChatId);

    Optional<Employee> findByTelegramLinkCode(String telegramLinkCode);

    boolean existsByUsername(String username);

    boolean existsByAdminTrue();
}
