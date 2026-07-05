package uz.sonic.hr.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import uz.sonic.hr.common.entity.UserDetailsImpl;
import uz.sonic.hr.admin.AdminRepository;
import uz.sonic.hr.employee.EmployeeRepository;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final EmployeeRepository employeeRepository;
    private final AdminRepository adminRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            JwtService.AuthToken token = jwtService.validate(header.substring(7));
            if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetailsImpl principal = token.admin()
                        ? adminRepository.findById(token.id()).filter(UserDetailsImpl::isEnabled).orElse(null)
                        : employeeRepository.findById(token.id()).filter(UserDetailsImpl::isEnabled).orElse(null);
                if (principal != null) {
                    var auth = new UsernamePasswordAuthenticationToken(
                            principal, null, principal.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
