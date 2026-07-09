package uz.sonic.hr.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import uz.sonic.hr.common.entity.UserDetailsImpl;
import uz.sonic.hr.admin.Admin;
import uz.sonic.hr.employee.Employee;
import uz.sonic.hr.admin.AdminRepository;
import uz.sonic.hr.employee.EmployeeRepository;
import uz.sonic.hr.common.security.JwtService;
import uz.sonic.hr.employee.EmployeeService;
import uz.sonic.hr.team.TeamService;
import uz.sonic.hr.common.dto.Dtos.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final EmployeeRepository employeeRepository;
    private final AdminRepository adminRepository;
    private final EmployeeService employeeService;
    private final TeamService teamService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        // The single project owner (admins table) takes precedence over employees.
        Admin admin = adminRepository.findByUsername(request.username())
                .filter(Admin::isActive)
                .filter(a -> passwordEncoder.matches(request.password(), a.getPassword()))
                .orElse(null);
        if (admin != null) {
            return new LoginResponse(
                    jwtService.generateToken(admin.getId(), true, admin.getUsername()),
                    EmployeeDto.fromAdmin(admin));
        }
        Employee employee = employeeRepository.findByUsername(request.username())
                .filter(Employee::isActive)
                .filter(e -> passwordEncoder.matches(request.password(), e.getPassword()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        return new LoginResponse(
                jwtService.generateToken(employee.getId(), false, employee.getUsername()),
                employeeDto(employee));
    }

    @PostMapping("/register")
    public LoginResponse register(@Valid @RequestBody RegisterRequest request) {
        Employee employee = employeeService.register(request);
        return new LoginResponse(
                jwtService.generateToken(employee.getId(), false, employee.getUsername()),
                employeeDto(employee));
    }

    @GetMapping("/me")
    public EmployeeDto me() {
        UserDetailsImpl principal = principal();
        return principal instanceof Admin admin
                ? EmployeeDto.fromAdmin(admin)
                : employeeDto((Employee) principal);
    }

    @PostMapping("/change-password")
    public EmployeeDto changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        UserDetailsImpl principal = principal();
        if (!passwordEncoder.matches(request.oldPassword(), principal.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Old password is incorrect");
        }
        principal.setPassword(passwordEncoder.encode(request.newPassword()));
        return save(principal);
    }

    /** Self-service account deletion (deactivate + anonymize). Password re-confirms the action. */
    @PostMapping("/delete-account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@Valid @RequestBody DeleteAccountRequest request) {
        UserDetailsImpl principal = principal();
        if (!(principal instanceof Employee employee)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The project owner account cannot be deleted here");
        }
        if (!passwordEncoder.matches(request.password(), employee.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is incorrect");
        }
        employeeService.deleteOwnAccount(employee);
    }

    @PostMapping("/language")
    public EmployeeDto updateLanguage(@Valid @RequestBody UpdateLanguageRequest request) {
        UserDetailsImpl principal = principal();
        principal.setLanguage(request.language());
        return save(principal);
    }

    private EmployeeDto save(UserDetailsImpl principal) {
        if (principal instanceof Admin admin) {
            return EmployeeDto.fromAdmin(adminRepository.save(admin));
        }
        return employeeDto(employeeRepository.save((Employee) principal));
    }

    private EmployeeDto employeeDto(Employee employee) {
        return EmployeeDto.from(employee, teamService.membershipsOf(employee));
    }

    private UserDetailsImpl principal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserDetailsImpl principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return principal;
    }
}
