package uz.sonic.hr.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.sonic.hr.admin.Admin;

import java.util.Optional;

public interface AdminRepository extends JpaRepository<Admin, Long> {

    Optional<Admin> findByUsername(String username);
}
