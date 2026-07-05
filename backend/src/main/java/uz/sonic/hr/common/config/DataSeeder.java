package uz.sonic.hr.common.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import uz.sonic.hr.admin.Admin;
import uz.sonic.hr.common.enums.Language;
import uz.sonic.hr.admin.AdminRepository;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password:admin123}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (adminRepository.count() == 0) {
            Admin admin = Admin.builder()
                    .fullName("Administrator")
                    .username(adminUsername)
                    .password(passwordEncoder.encode(adminPassword))
                    .language(Language.EN)
                    .build();
            adminRepository.save(admin);
            log.warn("Seeded project ADMIN: {} — change the password after first login!", adminUsername);
        }
    }
}
