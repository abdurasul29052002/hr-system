package uz.sonic.hr.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import uz.sonic.hr.repo.EmployeeRepository;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final EmployeeRepository employeeRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            Long employeeId = jwtService.validateAndGetEmployeeId(header.substring(7));
            if (employeeId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                employeeRepository.findById(employeeId)
                        .filter(uz.sonic.hr.entity.Employee::isActive)
                        .ifPresent(employee -> {
                            String authority = employee.isAdmin() ? "ROLE_ADMIN" : "ROLE_USER";
                            var auth = new UsernamePasswordAuthenticationToken(
                                    employee,
                                    null,
                                    List.of(new SimpleGrantedAuthority(authority)));
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        });
            }
        }
        filterChain.doFilter(request, response);
    }
}
