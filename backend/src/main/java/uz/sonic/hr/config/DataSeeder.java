package uz.sonic.hr.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import uz.sonic.hr.entity.Employee;
import uz.sonic.hr.entity.Language;
import uz.sonic.hr.repo.EmployeeRepository;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password:admin123}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (!employeeRepository.existsByAdminTrue()) {
            Employee admin = Employee.builder()
                    .fullName("Administrator")
                    .username(adminUsername)
                    .password(passwordEncoder.encode(adminPassword))
                    .admin(true)
                    .language(Language.EN)
                    .telegramLinkCode("ADMIN0")
                    .build();
            employeeRepository.save(admin);
            log.warn("Seeded project ADMIN: {} — change the password after first login!", adminUsername);
        }
    }
}
