package uz.sonic.hr.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.userdetails.UserDetails;
import uz.sonic.hr.common.enums.Language;
import uz.sonic.hr.common.enums.Role;

/**
 * Common authenticatable account base for {@code Employee} and {@code Admin}.
 * Each concrete subclass maps to its own table but shares identity, credentials
 * and Spring Security {@link UserDetails} wiring. Authorities are subclass-specific.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@MappedSuperclass
public abstract class UserDetailsImpl extends BaseEntity implements UserDetails {

    @Column(nullable = false)
    protected String fullName;

    @Column(nullable = false, unique = true)
    protected String username;

    @Column(nullable = false)
    protected String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    protected Language language = Language.EN;

    @Column(nullable = false)
    @Builder.Default
    protected boolean active = true;

    protected Role role;

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
