package uz.sonic.hr.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import uz.sonic.hr.entity.Employee;

@Component
public class CurrentUser {

    public Employee get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Employee employee)) {
            throw new IllegalStateException("No authenticated employee");
        }
        return employee;
    }
}
