package uz.sonic.hr.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import uz.sonic.hr.employee.Employee;
import uz.sonic.hr.employee.EmployeeRepository;

@Component
@RequiredArgsConstructor
public class CurrentUser {

    private final EmployeeRepository employeeRepository;

    /** The managed Employee for the authenticated principal (403 if the caller is the admin). */
    public Employee get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Employee employee) {
            return employeeRepository.findById(employee.getId())
                    .orElseThrow(() -> new IllegalStateException("Authenticated employee not found"));
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Employee account required");
    }
}
