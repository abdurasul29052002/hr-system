package uz.sonic.hr.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import uz.sonic.hr.entity.Employee;
import uz.sonic.hr.repo.EmployeeRepository;
import uz.sonic.hr.security.CurrentUser;
import uz.sonic.hr.security.JwtService;
import uz.sonic.hr.service.EmployeeService;
import uz.sonic.hr.service.TeamService;
import uz.sonic.hr.web.dto.Dtos.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final EmployeeRepository employeeRepository;
    private final EmployeeService employeeService;
    private final TeamService teamService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUser currentUser;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Employee employee = employeeRepository.findByUsername(request.username())
                .filter(Employee::isActive)
                .filter(e -> passwordEncoder.matches(request.password(), e.getPassword()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        return new LoginResponse(jwtService.generateToken(employee), toDto(employee));
    }

    @PostMapping("/register")
    public LoginResponse register(@Valid @RequestBody RegisterRequest request) {
        Employee employee = employeeService.register(request);
        return new LoginResponse(jwtService.generateToken(employee), toDto(employee));
    }

    @GetMapping("/me")
    public EmployeeDto me() {
        return toDto(currentUser.get());
    }

    @PostMapping("/change-password")
    public EmployeeDto changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Employee employee = currentUser.get();
        if (!passwordEncoder.matches(request.oldPassword(), employee.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Old password is incorrect");
        }
        employee.setPassword(passwordEncoder.encode(request.newPassword()));
        return toDto(employeeRepository.save(employee));
    }

    @PostMapping("/language")
    public EmployeeDto updateLanguage(@Valid @RequestBody UpdateLanguageRequest request) {
        Employee employee = currentUser.get();
        employee.setLanguage(request.language());
        return toDto(employeeRepository.save(employee));
    }

    private EmployeeDto toDto(Employee employee) {
        return EmployeeDto.from(employee, teamService.membershipsOf(employee));
    }
}
