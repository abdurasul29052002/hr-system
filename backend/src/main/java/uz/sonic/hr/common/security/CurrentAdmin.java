package uz.sonic.hr.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import uz.sonic.hr.admin.Admin;
import uz.sonic.hr.admin.AdminRepository;

@Component
@RequiredArgsConstructor
public class CurrentAdmin {

    private final AdminRepository adminRepository;

    /** The managed Admin for the authenticated principal (403 if the caller is not the admin). */
    public Admin get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Admin admin) {
            return adminRepository.findById(admin.getId())
                    .orElseThrow(() -> new IllegalStateException("Authenticated admin not found"));
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin account required");
    }
}
